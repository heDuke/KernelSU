package me.weishu.kernelsu.data.model

/** Discrete content sizes, rather than fixed pixel heights. */
enum class DashboardSize { Compact, Standard, Expanded }
enum class DashboardCard { Status, Access, Runtime, Incidents, Todos, Shortcuts, Versions, Modules, Device, Compatibility }
enum class DashboardShortcut { InstallModule, Flash, Diagnostics, Reboot, CheckUpdates, Loader, DisableModules, RestoreModules }

data class DashboardLayout(
    val sizes: Map<DashboardCard, DashboardSize> = DashboardCard.entries.associateWith {
        if (it.ordinal < 4) DashboardSize.Compact else DashboardSize.Standard
    },
    val shortcuts: List<DashboardShortcut> = listOf(DashboardShortcut.InstallModule, DashboardShortcut.Flash,
        DashboardShortcut.Diagnostics, DashboardShortcut.Reboot),
) {
    fun serialize(): String = "1\n" + DashboardCard.entries.joinToString(",") { "${it.name}=${sizes.getValue(it).name}" } +
        "\n" + shortcuts.joinToString(",") { it.name }

    companion object {
        fun parse(text: String): DashboardLayout {
            require(text.length <= 2048)
            val lines = text.split('\n')
            require(lines.size == 3 && lines[0] == "1")
            val sizes = linkedMapOf<DashboardCard, DashboardSize>()
            lines[1].split(',').forEach {
                val field = it.split('='); require(field.size == 2)
                val card = DashboardCard.valueOf(field[0]); require(card !in sizes)
                sizes[card] = DashboardSize.valueOf(field[1])
            }
            require(sizes.keys == DashboardCard.entries.toSet())
            val shortcuts = lines[2].split(',').map(DashboardShortcut::valueOf)
            require(shortcuts.size in 1..6 && shortcuts.distinct().size == shortcuts.size)
            return DashboardLayout(sizes, shortcuts)
        }
    }
}

data class RuntimeIncident(val at: Long, val code: String)
data class RuntimeRecord(
    val boot: String = "", val lastElapsed: Long = 0, val observedSince: Long? = null,
    val seenActive: Boolean = false, val activeFaults: Set<String> = emptySet(),
    val total: Long = 0, val events: List<RuntimeIncident> = emptyList(), val startedAt: Long? = null,
) {
    /** Counts distinct observed fault episodes; never fabricates causes between observations. */
    fun observe(bootId: String, elapsed: Long, wall: Long, active: Boolean, faults: Set<String>): RuntimeRecord {
        require(elapsed >= 0 && wall >= 0 && faults.size <= 16)
        val sameBoot = bootId.isNotEmpty() && boot == bootId && elapsed >= lastElapsed
        val previousFaults = if (sameBoot || bootId.isEmpty()) activeFaults else emptySet()
        val seen = active || (sameBoot && seenActive)
        val effective = faults + if (!active && seen) setOf("activation_lost") else emptySet()
        val fresh = effective - previousFaults
        val continuous = sameBoot && lastElapsed > 0 && elapsed - lastElapsed <= 90_000
        val normal = active && effective.isEmpty()
        return RuntimeRecord(bootId, elapsed,
            if (normal && bootId.isNotEmpty()) observedSince?.takeIf { continuous } ?: elapsed else null,
            seen, effective, total.coerceIn(0, Long.MAX_VALUE - fresh.size) + fresh.size,
            (events + fresh.sorted().map { RuntimeIncident(wall, it) }).takeLast(40), startedAt ?: wall)
    }
    fun pause(): RuntimeRecord = copy(observedSince = null)
}
