package me.weishu.kernelsu.core.shell;

import java.io.*;
import org.junit.Test;
import static org.junit.Assume.assumeTrue;
import java.nio.file.*;
import java.util.concurrent.*;
import me.weishu.kernelsu.core.shell.ShellIdentity;
import me.weishu.kernelsu.core.shell.TempShellCommandsKt;

/** Linux-host probe of generated commands, signalling only this probe's new process group. */
public class TempShellCommandsTest {
    @Test public void normalShellExitAlsoCleansBackgroundJobs() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")) && Files.isExecutable(Path.of("/usr/bin/setsid")));
        Process shell = new ProcessBuilder("/bin/sh").start();
        ExecutorService readerThread = Executors.newSingleThreadExecutor();
        try {
            var input = new OutputStreamWriter(shell.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(shell.getInputStream()));
            input.write(TempShellCommandsKt.temporaryShellBootstrap("PROBE:")
                .replace("/system/bin/toybox setsid", "setsid").replace("/system/bin/sh", "/bin/sh") + "\n");
            input.flush();
            String hello = readerThread.submit(reader::readLine).get(5, TimeUnit.SECONDS);
            if (hello == null || !hello.startsWith("PROBE:")) throw new AssertionError("No handshake");
            input.write("sleep 10 & echo CHILD:$!; exit\n"); input.flush();
            String childLine = readerThread.submit(reader::readLine).get(5, TimeUnit.SECONDS);
            int child = Integer.parseInt(childLine.substring(6));
            if (!shell.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("Session leader did not exit");
            Path stat = Path.of("/proc", Integer.toString(child), "stat");
            if (Files.exists(stat)) {
                String value = Files.readString(stat);
                if (!value.substring(value.lastIndexOf(')') + 2).startsWith("Z "))
                    throw new AssertionError("Background command outlived its session");
            }
        } finally { shell.destroyForcibly(); readerThread.shutdownNow(); }
    }

    @Test public void isolatesChecksIdentityAndCleansOwnedDescendants() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")) && Files.isExecutable(Path.of("/usr/bin/setsid")));
        Process shell = new ProcessBuilder("/bin/sh").start();
        ExecutorService readerThread = Executors.newSingleThreadExecutor();
        try {
            var input = new OutputStreamWriter(shell.getOutputStream());
            var reader = new BufferedReader(new InputStreamReader(shell.getInputStream()));
            String bootstrap = TempShellCommandsKt.temporaryShellBootstrap("PROBE:")
                .replace("/system/bin/toybox setsid", "setsid").replace("/system/bin/sh", "/bin/sh");
            input.write(bootstrap + "\n"); input.flush();
            String hello = readerThread.submit(reader::readLine).get(5, TimeUnit.SECONDS);
            if (hello == null || !hello.startsWith("PROBE:")) throw new AssertionError("No handshake");
            String[] fields = hello.substring(6).split(":");
            int pid = Integer.parseInt(fields[0]);
            int group = Integer.parseInt(fields[2]);
            long ticks = Long.parseLong(fields[3]);
            if (pid <= 1 || pid != group) throw new AssertionError("Not an isolated process group");
            ShellIdentity owner = new ShellIdentity(pid, group, ticks);
            String stale = TempShellCommandsKt.temporaryShellSignal(new ShellIdentity(pid, group, ticks + 1), false);
            Process rejected = new ProcessBuilder("/bin/sh", "-c", stale).start();
            if (!rejected.waitFor(5, TimeUnit.SECONDS) || rejected.exitValue() == 0 || !shell.isAlive())
                throw new AssertionError("PID reuse guard did not reject stale identity");
            input.write("sleep 10 & echo CHILD:$!; wait\n"); input.flush();
            String childLine = readerThread.submit(reader::readLine).get(5, TimeUnit.SECONDS);
            int child = Integer.parseInt(childLine.substring(6));
            Process signal = new ProcessBuilder("/bin/sh", "-c", TempShellCommandsKt.temporaryShellSignal(owner, false)).start();
            if (!signal.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("Signal helper timeout");
            String signalError = new String(signal.getErrorStream().readAllBytes());
            if (signal.exitValue() != 0 || !shell.waitFor(5, TimeUnit.SECONDS))
                throw new AssertionError("Owned process group did not exit; helper=" + signal.exitValue() + "; stderr=" + signalError);
            Path stat = Path.of("/proc", Integer.toString(child), "stat");
            if (Files.exists(stat)) {
                String value = Files.readString(stat);
                if (!value.substring(value.lastIndexOf(')') + 2).startsWith("Z "))
                    throw new AssertionError("Command descendant still running");
            }

        } finally {
            shell.destroyForcibly();
            readerThread.shutdownNow();
        }
    }
}
