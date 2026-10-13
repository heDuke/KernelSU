package me.weishu.kernelsu.data.model

/** Preference-only interchange with the independent CLI; target approval is never configurable. */
data class ExperimentalLoaderSettings(
    val generation: Long = 0,
    val preset: String = "balanced",
    val overrides: Map<String, String> = emptyMap(),
) {
    fun effective(): Map<String, String> = defaults + presetValues(preset) + overrides
    fun withPreset(name: String) = copy(preset = name, overrides = overrides - presetValues(name).keys)
    fun serialize(): String = buildString {
        append("schema=1\ngeneration=$generation\npreset=$preset\n")
        overrides.toSortedMap().forEach { (key, value) -> append("$key=$value\n") }
    }
    companion object {
        private val defaults = mapOf("output" to "json", "kernel_log" to "true", "module_limit_mib" to "16",
            "cache_enabled" to "false", "deadline_seconds" to "5")
        private fun presetValues(name: String): Map<String, String> {
            val fields = when (name) {
                "balanced" -> listOf("normal", "32", "0", "16")
                "lean" -> listOf("error", "8", "0", "8")
                "diagnostic" -> listOf("debug", "64", "0", "32")
                else -> error("Unknown preset")
            }
            return listOf("verbosity", "log_limit_kib", "history_count", "io_buffer_kib").zip(fields).toMap()
        }
        fun parse(bytes: ByteArray): ExperimentalLoaderSettings {
            require(bytes.size <= 16384) { "Settings exceed 16 KiB" }
            val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            val fields = LinkedHashMap<String, String>()
            text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.forEach { line ->
                require(line.length <= 1024 && fields.size < 64) { "Settings record limit exceeded" }
                val pair = line.split('=', limit = 2)
                require(pair.size == 2 && pair[1].isNotEmpty() && !fields.containsKey(pair[0])) { "Empty or duplicate field" }
                fields[pair[0]] = pair[1]
            }
            require(fields.remove("schema") == "1") { "Unsupported settings schema" }
            val rawGeneration = fields.remove("generation") ?: "0"
            require(rawGeneration.all { it in '0'..'9' }) { "Invalid generation" }
            val generation = requireNotNull(rawGeneration.toLongOrNull()) { "Invalid generation" }
            require(generation >= 0) { "Invalid generation" }
            val preset = fields.remove("preset") ?: "balanced"
            presetValues(preset)
            fields.forEach { (key, value) ->
                val number = value.toIntOrNull()
                val valid = when (key) {
                    "output" -> value in listOf("json", "text")
                    "verbosity" -> value in listOf("error", "normal", "debug")
                    "kernel_log" -> value in listOf("true", "false")
                    "log_limit_kib" -> number != null && value.all { it in '0'..'9' } && number in 4..64
                    "history_count" -> value == "0"
                    "io_buffer_kib" -> value in listOf("8", "16", "32", "64")
                    "module_limit_mib" -> number != null && value.all { it in '0'..'9' } && number in 1..16
                    "cache_enabled" -> value == "false"
                    "deadline_seconds" -> number != null && value.all { it in '0'..'9' } && number in 1..30
                    else -> false
                }
                require(valid) { "Invalid, unsupported or immutable preference: $key" }
            }
            return ExperimentalLoaderSettings(generation, preset, fields.toMap())
        }
    }
}
