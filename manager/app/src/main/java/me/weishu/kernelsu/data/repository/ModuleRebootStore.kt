package me.weishu.kernelsu.data.repository

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.data.model.PendingModuleChanges
import me.weishu.kernelsu.ksuApp
import java.io.File

/** Records successful module mutations; never derives pending work from disabled modules. */
object ModuleRebootStore {
    private val prefs by lazy { ksuApp.getSharedPreferences("module-pending-reboot", Context.MODE_PRIVATE) }
    data class Value(val record: PendingModuleChanges = PendingModuleChanges(), val error: String? = null)
    private val mutable = MutableStateFlow(Value())
    val state = mutable.asStateFlow()

    private fun boot(): String = runCatching {
        "count:" + Settings.Global.getInt(ksuApp.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrElse {
        runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim().takeIf(String::isNotEmpty)?.let { "id:$it" }.orEmpty() }.getOrDefault("")
    }

    @Synchronized private fun update(changed: Boolean) {
        try {
            val persisted = PendingModuleChanges(prefs.getString("boot", "").orEmpty(), prefs.getBoolean("pending", false))
            val old = if (mutable.value.error != null && mutable.value.record.pending) mutable.value.record else persisted
            val next = if (changed) old.changed(boot()) else old.observe(boot())
            if (next != persisted || mutable.value.error != null) check(prefs.edit().putString("boot", next.boot).putBoolean("pending", next.pending).commit()) {
                "Cannot persist pending-reboot observation"
            }
            mutable.value = Value(next)
        } catch (failure: Exception) {
            mutable.value = Value(mutable.value.record.copy(pending = changed || mutable.value.record.pending), failure.message ?: "Record unavailable")
        }
    }

    fun refreshBoot() = update(false)
    fun markChanged() = update(true)
}
