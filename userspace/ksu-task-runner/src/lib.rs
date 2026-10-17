//! User-initiated, noninteractive jobs. Control records never come from command output.
#![cfg(any(target_os = "linux", target_os = "android"))]
use std::io::{self, Read, Write};
use std::os::unix::process::{CommandExt, ExitStatusExt};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{self, Receiver, TryRecvError};
use std::time::{Duration, Instant};

const COMMAND_LIMIT: usize = 65536;
static TERMINATING: AtomicBool = AtomicBool::new(false);

extern "C" fn terminate(_: libc::c_int) {
    TERMINATING.store(true, Ordering::Relaxed);
}

/// Framed protocol: one tag, big-endian u32 length, then opaque payload.
/// H=verified worker identity, R=child started, O/E=output, X=observed wait result.
fn frame(out: &mut impl Write, tag: u8, data: &[u8]) -> io::Result<()> {
    let size = u32::try_from(data.len()).map_err(io::Error::other)?;
    out.write_all(&[tag])?;
    out.write_all(&size.to_be_bytes())?;
    out.write_all(data)?;
    out.flush()
}

fn start_ticks(pid: u32) -> io::Result<u64> {
    let stat = std::fs::read_to_string(format!("/proc/{pid}/stat"))?;
    stat.rsplit_once(") ")
        .and_then(|(_, tail)| tail.split_whitespace().nth(19))
        .and_then(|s| s.parse().ok())
        .ok_or_else(|| io::Error::other("Missing process identity"))
}

fn read_command(input: &mut impl Read) -> io::Result<String> {
    let mut size = [0; 4];
    input.read_exact(&mut size)?;
    let size = u32::from_be_bytes(size) as usize;
    if size == 0 || size > COMMAND_LIMIT {
        return Err(io::Error::other("Invalid command size"));
    }
    let mut bytes = vec![0; size];
    input.read_exact(&mut bytes)?;
    let command = String::from_utf8(bytes).map_err(io::Error::other)?;
    if command.trim().is_empty() || command.contains('\0') {
        return Err(io::Error::other("Invalid command"));
    }
    Ok(command)
}

#[derive(Clone, Copy)]
enum Control {
    Interrupt,
    Stop,
}

fn pump(mut input: impl Read + Send + 'static, tag: u8, tx: mpsc::SyncSender<(u8, Vec<u8>)>) {
    std::thread::spawn(move || {
        let mut buffer = [0; 8192];
        loop {
            match input.read(&mut buffer) {
                Ok(0) | Err(_) => break,
                Ok(n) => {
                    if tx.send((tag, buffer[..n].to_vec())).is_err() {
                        break;
                    }
                }
            }
        }
    });
}

/// Kill only the still-owned child group, before its leader's wait result is consumed.
fn signal_group(pid: u32, signal: i32) -> io::Result<()> {
    let pid = i32::try_from(pid).map_err(io::Error::other)?;
    if pid <= 1 {
        return Err(io::Error::other("Invalid process group"));
    }
    // This ID belongs to an unreaped child created with process_group(0).
    if unsafe { libc::kill(-pid, signal) } == -1 {
        let error = io::Error::last_os_error();
        if error.raw_os_error() != Some(libc::ESRCH) {
            return Err(error);
        }
    }
    Ok(())
}

fn exited_unreaped(pid: u32) -> io::Result<bool> {
    // WNOWAIT retains the group leader identity until ordinary descendants have
    // received cleanup, preventing a recycled numeric PID from being signalled.
    let mut info: libc::siginfo_t = unsafe { std::mem::zeroed() };
    let result = unsafe {
        libc::waitid(
            libc::P_PID,
            pid,
            &mut info,
            libc::WEXITED | libc::WNOHANG | libc::WNOWAIT,
        )
    };
    if result != 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(unsafe { info.si_pid() } != 0)
}

fn supervise(
    command: &str,
    shell: &str,
    controls: &Receiver<Control>,
    out: &mut impl Write,
) -> io::Result<()> {
    let parent = std::process::id();
    let mut builder = Command::new(shell);
    builder
        .args(["-c", command])
        .current_dir("/")
        .env("LC_ALL", "C")
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .process_group(0);
    // Only async-signal-safe operations are used between fork and exec.
    unsafe {
        builder.pre_exec(move || {
            if libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGKILL) != 0 {
                return Err(io::Error::last_os_error());
            }
            if libc::getppid() != parent as i32 {
                return Err(io::Error::other("Worker disappeared"));
            }
            Ok(())
        });
    }
    let mut child = builder.spawn()?;
    let pid = child.id();
    let (tx, rx) = mpsc::sync_channel(16);
    pump(child.stdout.take().unwrap(), b'O', tx.clone());
    pump(child.stderr.take().unwrap(), b'E', tx.clone());
    drop(tx);
    let mut reaped = false;
    let result = (|| {
        frame(out, b'R', format!("{pid}:{}", start_ticks(pid)?).as_bytes())?;
        let mut stop_at = None;
        let mut killed = false;
        loop {
            let mut disconnected = false;
            loop {
                match controls.try_recv() {
                    Ok(Control::Interrupt) => signal_group(pid, libc::SIGINT)?,
                    Ok(Control::Stop) => {
                        disconnected = true;
                        break;
                    }
                    Err(TryRecvError::Disconnected) => {
                        disconnected = true;
                        break;
                    }
                    Err(TryRecvError::Empty) => break,
                }
            }
            if (disconnected || TERMINATING.load(Ordering::Relaxed)) && stop_at.is_none() {
                signal_group(pid, libc::SIGTERM)?;
                stop_at = Some(Instant::now());
            }
            if !killed && stop_at.is_some_and(|at| at.elapsed() >= Duration::from_secs(5)) {
                signal_group(pid, libc::SIGKILL)?;
                killed = true;
            }
            match rx.recv_timeout(Duration::from_millis(30)) {
                Ok((tag, bytes)) => frame(out, tag, &bytes)?,
                Err(mpsc::RecvTimeoutError::Timeout | mpsc::RecvTimeoutError::Disconnected) => {}
            }
            if exited_unreaped(pid)? {
                signal_group(pid, libc::SIGKILL)?;
                let status = child.wait()?;
                reaped = true;
                // Bound draining; detached descendants holding pipe FDs are not managed jobs.
                let until = Instant::now() + Duration::from_millis(500);
                while Instant::now() < until {
                    match rx.recv_timeout(Duration::from_millis(10)) {
                        Ok((tag, bytes)) => frame(out, tag, &bytes)?,
                        Err(mpsc::RecvTimeoutError::Disconnected) => break,
                        Err(mpsc::RecvTimeoutError::Timeout) => {}
                    }
                }
                frame(
                    out,
                    b'X',
                    format!(
                        "{}:{}",
                        status.code().unwrap_or(-1),
                        status.signal().unwrap_or(0)
                    )
                    .as_bytes(),
                )?;
                return Ok(());
            }
        }
    })();
    if result.is_err() && !reaped {
        // Do not reap before signaling: retained ownership prevents numeric PID reuse.
        let _ = signal_group(pid, libc::SIGKILL);
        let _ = child.wait();
    }
    result
}

/// Called only after the existing manager-authorized `debug su` bootstrap.
pub fn run() -> io::Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        return Err(io::Error::other("Root identity required"));
    }
    TERMINATING.store(false, Ordering::Relaxed);
    unsafe {
        libc::signal(libc::SIGTERM, terminate as *const () as libc::sighandler_t);
        if libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGTERM) != 0 {
            return Err(io::Error::last_os_error());
        }
    }
    let mut input = io::stdin();
    let mut out = io::stdout();
    let pid = std::process::id();
    frame(
        &mut out,
        b'H',
        format!("0:{pid}:{}", start_ticks(pid)?).as_bytes(),
    )?;
    let command = read_command(&mut input)?;
    let (tx, controls) = mpsc::channel();
    std::thread::spawn(move || {
        let mut byte = [0];
        while input.read_exact(&mut byte).is_ok() {
            let control = match byte[0] {
                b'I' => Control::Interrupt,
                b'T' => Control::Stop,
                _ => break,
            };
            if tx.send(control).is_err() {
                break;
            }
        }
        // Dropping the only sender requests cleanup on EOF or invalid control input.
    });
    supervise(&command, "/system/bin/sh", &controls, &mut out)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn records(mut bytes: &[u8]) -> Vec<(u8, Vec<u8>)> {
        let mut records = Vec::new();
        while !bytes.is_empty() {
            let tag = bytes[0];
            let len = u32::from_be_bytes(bytes[1..5].try_into().unwrap()) as usize;
            records.push((tag, bytes[5..5 + len].to_vec()));
            bytes = &bytes[5 + len..];
        }
        records
    }
    #[test]
    fn output_cannot_spoof_status_and_exit_code_is_observed() {
        let (_keep_alive, controls) = mpsc::channel();
        let mut out = Vec::new();
        supervise(
            "printf 'X\\000fake'; printf 'error' >&2; exit 7",
            "/bin/sh",
            &controls,
            &mut out,
        )
        .unwrap();
        let records = records(&out);
        assert_eq!(records.last().unwrap(), &(b'X', b"7:0".to_vec()));
        assert!(
            records
                .iter()
                .any(|(tag, data)| *tag == b'O' && data.starts_with(b"X\0fake"))
        );
        assert!(
            records
                .iter()
                .any(|(tag, data)| *tag == b'E' && data == b"error")
        );
    }
    #[test]
    fn controller_eof_stops_owned_process() {
        let (tx, controls) = mpsc::channel();
        drop(tx);
        let mut out = Vec::new();
        let started = Instant::now();
        supervise("exec sleep 60", "/bin/sh", &controls, &mut out).unwrap();
        assert!(started.elapsed() < Duration::from_secs(3));
        assert_eq!(records(&out).last().unwrap(), &(b'X', b"-1:15".to_vec()));
    }
    #[test]
    fn ordinary_background_descendants_do_not_outlive_a_completed_job() {
        let (_keep_alive, controls) = mpsc::channel();
        let mut out = Vec::new();
        supervise(
            "sleep 60 & printf '%s' \"$!\"; exit 0",
            "/bin/sh",
            &controls,
            &mut out,
        )
        .unwrap();
        let records = records(&out);
        let bytes: Vec<u8> = records
            .iter()
            .filter(|(tag, _)| *tag == b'O')
            .flat_map(|(_, bytes)| bytes.clone())
            .collect();
        let pid: u32 = String::from_utf8(bytes).unwrap().parse().unwrap();
        assert!(pid > 1);
        let until = Instant::now() + Duration::from_secs(2);
        loop {
            let stat = std::fs::read_to_string(format!("/proc/{pid}/stat"));
            if stat
                .as_ref()
                .is_err_and(|e| e.kind() == io::ErrorKind::NotFound)
            {
                break;
            }
            if stat.as_ref().is_ok_and(|s| {
                s.rsplit_once(") ")
                    .is_some_and(|(_, tail)| tail.starts_with("Z "))
            }) {
                break;
            }
            assert!(Instant::now() < until, "Owned descendant remained alive");
            std::thread::sleep(Duration::from_millis(10));
        }
        assert_eq!(records.last().unwrap(), &(b'X', b"0:0".to_vec()));
    }

    #[test]
    fn stop_escalates_only_after_grace_period() {
        struct StopOnReady {
            bytes: Vec<u8>,
            controls: mpsc::Sender<Control>,
        }
        impl Write for StopOnReady {
            fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
                self.bytes.extend(bytes);
                if bytes == b"ready" {
                    self.controls.send(Control::Stop).unwrap();
                }
                Ok(bytes.len())
            }
            fn flush(&mut self) -> io::Result<()> {
                Ok(())
            }
        }
        let (tx, controls) = mpsc::channel();
        let mut out = StopOnReady {
            bytes: Vec::new(),
            controls: tx,
        };
        let started = Instant::now();
        supervise(
            "trap '' TERM; printf ready; while :; do sleep 1; done",
            "/bin/sh",
            &controls,
            &mut out,
        )
        .unwrap();
        assert!(started.elapsed() >= Duration::from_secs(5));
        assert!(started.elapsed() < Duration::from_secs(15));
        assert_eq!(
            records(&out.bytes).last().unwrap(),
            &(b'X', b"-1:9".to_vec())
        );
    }

    #[test]
    fn command_framing_rejects_overflow_and_preserves_shell_text() {
        assert!(read_command(&mut io::Cursor::new((65537u32).to_be_bytes())).is_err());
        let command = " printf '%s' '$PATH'\nprintf second ";
        let mut encoded = (command.len() as u32).to_be_bytes().to_vec();
        encoded.extend(command.as_bytes());
        assert_eq!(
            read_command(&mut io::Cursor::new(encoded)).unwrap(),
            command
        );
    }
}
