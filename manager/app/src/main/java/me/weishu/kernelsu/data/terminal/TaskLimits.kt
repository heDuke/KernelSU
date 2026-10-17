package me.weishu.kernelsu.data.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.ksuApp

data class TaskLimits(val queued: Int = 16, val records: Int = 100, val days: Int = 7, val logMiB: Int = 2, val totalMiB: Int = 20) {
    fun validate() { require(queued in 1..16 && records in 10..100 && days in 1..30 && logMiB in 1..2 && totalMiB in 5..20) }
}
object TaskLimitSettings {
    private val prefs by lazy { ksuApp.getSharedPreferences("terminal-task-limits", android.content.Context.MODE_PRIVATE) }
    private val mutable = MutableStateFlow(TaskLimits(prefs.getInt("queued", 16).coerceIn(1,16), prefs.getInt("records",100).coerceIn(10,100),
        prefs.getInt("days",7).coerceIn(1,30), prefs.getInt("log",2).coerceIn(1,2), prefs.getInt("total",20).coerceIn(5,20)))
    val limits = mutable.asStateFlow()
    fun save(value: TaskLimits) { value.validate(); prefs.edit().putInt("queued",value.queued).putInt("records",value.records).putInt("days",value.days).putInt("log",value.logMiB).putInt("total",value.totalMiB).apply(); mutable.value=value }
}
