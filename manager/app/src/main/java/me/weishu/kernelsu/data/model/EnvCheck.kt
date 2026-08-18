package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

enum class EnvCheckSeverity {
    Pass,
    Warn,
    Fail,
    Unknown,
}

enum class EnvCheckGroup {
    Root,
    Version,
    Boot,
    IntegrityOnline,
    Modules,
    System,
}

@Immutable
data class EnvCheckItem(
    val id: String,
    val group: EnvCheckGroup,
    val title: String,
    val detail: String,
    val severity: EnvCheckSeverity,
    val rawValue: String = "",
)

@Immutable
data class EnvCheckReport(
    val items: List<EnvCheckItem>,
    val generatedAtEpochMs: Long,
) {
    val overall: EnvCheckSeverity
        get() = when {
            items.any { it.severity == EnvCheckSeverity.Fail } -> EnvCheckSeverity.Fail
            items.any { it.severity == EnvCheckSeverity.Warn } -> EnvCheckSeverity.Warn
            items.any { it.severity == EnvCheckSeverity.Unknown } -> EnvCheckSeverity.Unknown
            else -> EnvCheckSeverity.Pass
        }

    val worstItem: EnvCheckItem?
        get() = items.firstOrNull { it.severity == EnvCheckSeverity.Fail }
            ?: items.firstOrNull { it.severity == EnvCheckSeverity.Warn }
            ?: items.firstOrNull { it.severity == EnvCheckSeverity.Unknown }

    fun toPlainReport(): String = buildString {
        appendLine("HuskySU environment check")
        appendLine("generated=$generatedAtEpochMs overall=$overall")
        appendLine()
        items.groupBy { it.group }.forEach { (group, groupItems) ->
            appendLine("[$group]")
            groupItems.forEach { item ->
                appendLine("- ${item.severity} | ${item.title}: ${item.detail}")
                if (item.rawValue.isNotBlank()) {
                    appendLine("  raw=${item.rawValue}")
                }
            }
            appendLine()
        }
    }
}
