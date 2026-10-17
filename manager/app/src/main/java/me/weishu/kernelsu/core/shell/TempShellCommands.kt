package me.weishu.kernelsu.core.shell

/** A private process group lets session exit clean up ordinary command descendants. */
fun temporaryShellBootstrap(marker: String): String {
    require(marker.matches(Regex("[A-Za-z0-9_:-]+")))
    val handshake =
        "s=\"\$(cat /proc/\$\$/stat)\"; s=\"\${s##*) }\"; set -- \$s; " +
        "printf \"$marker%s:%s:%s:%s\\n\" \"\$\$\" \"\$(id -u)\" \"\$3\" \"\${20}\"; /system/bin/sh"
    // Keep the group leader alive while the command shell runs. Its exit trap also
    // cleans up ordinary background jobs when the command shell exits by itself.
    return "exec /system/bin/toybox setsid /system/bin/sh -c 'trap \"kill -KILL -\$\$\" EXIT; $handshake'"
}

/** Check PID reuse and group ownership before signalling anything. */
fun temporaryShellSignal(owner: ShellIdentity, interrupt: Boolean): String {
    require(owner.pid > 1 && owner.processGroup == owner.pid && owner.startTicks > 0)
    val guard = "s=\"\$(cat /proc/${owner.pid}/stat)\" || exit 1; " +
        "s=\"\${s##*) }\"; set -- \$s; " +
        "[ \"\$3\" = '${owner.pid}' ] && [ \"\${20}\" = '${owner.startTicks}' ] || exit 1; "
    return guard + if (interrupt) "result=1; for child in \$(pgrep -P ${owner.pid}); do pkill -INT -P \"\$child\" && result=0; done; exit \"\$result\""
    else "kill -KILL -${owner.pid}"
}
