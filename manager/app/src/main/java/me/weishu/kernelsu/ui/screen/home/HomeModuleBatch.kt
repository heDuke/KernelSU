package me.weishu.kernelsu.ui.screen.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.repository.ModuleRepositoryImpl
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.component.module.ModuleInstallController
import me.weishu.kernelsu.ui.util.PackageClient
import me.weishu.kernelsu.ui.util.hasMagisk
import me.weishu.kernelsu.ui.util.rootAvailable
import org.json.JSONArray
import org.json.JSONObject

/** An explicit user-owned batch; never enables modules outside the recorded batch. */
internal object HomeModuleBatch {
    private val mutex = Mutex()
    suspend fun run(restore: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        if (!me.weishu.kernelsu.core.utils.ModuleMutationGate.tryAcquire())
            return@withContext Result.failure(IllegalStateException("Another module operation is running"))
        try { mutex.withLock {
            runCatching {
                check(Natives.isManager && rootAvailable() && !Natives.isSafeMode && !hasMagisk()) { "Root/module environment is unavailable" }
                check(!ModuleInstallController.state.value.busy) { "An installation is running" }
                val prefs = ksuApp.getSharedPreferences("home-module-batch", Context.MODE_PRIVATE)
                check(!prefs.contains("pending")) { "Previous batch was interrupted; inspect its diagnostic record before another batch" }
                val raw = prefs.getString("applied", "[]") ?: "[]"
                require(raw.length <= 65_536)
                val array = JSONArray(raw); require(array.length() <= 512)
                val recorded = linkedMapOf<String, Int>()
                for (i in 0 until array.length()) array.getJSONObject(i).let { recorded[it.getString("id")] = it.getInt("version") }
                val modules = ModuleRepositoryImpl().getModules().getOrThrow()
                val targets = if (restore) modules.filter { !it.enabled && !it.remove && recorded[it.id] == it.versionCode }
                    else modules.filter { it.enabled && !it.remove }
                require(targets.size <= 512)
                check(restore || recorded.isEmpty()) { "Restore or review the previous batch before replacing its record" }
                fun save() {
                    val json = JSONArray(recorded.map { (id, version) -> JSONObject().put("id", id).put("version", version) })
                    check(prefs.edit().putString("applied", json.toString()).commit()) { "Cannot persist batch result" }
                }
                var changed = 0
                for (module in targets) {
                    require(module.id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")))
                    check(!ModuleInstallController.state.value.busy && Natives.isManager && !Natives.isSafeMode) { "Module environment changed" }
                    check(prefs.edit().putString("pending", module.id).commit()) { "Cannot journal module change" }
                    check(PackageClient.setModuleEnabled(module.id, restore)) { "Module change failed: ${module.name}; $changed completed" }
                    if (restore) recorded.remove(module.id) else recorded[module.id] = module.versionCode
                    save()
                    check(prefs.edit().remove("pending").commit()) { "Cannot finalize module change record" }
                    changed++
                }
                if (restore) {
                    // Removed/changed packages are intentionally skipped and released from this batch.
                    recorded.clear(); save()
                }
                changed
            }
        } } finally { me.weishu.kernelsu.core.utils.ModuleMutationGate.release() }
    }
}

@androidx.compose.runtime.Composable
internal fun HomeBatchRecoveryPanel(enabled: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("home-module-batch", Context.MODE_PRIVATE) }
    var pending by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(prefs.getString("pending", null)) }
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (pending != null) {
        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(me.weishu.kernelsu.R.string.dashboard_batch_interrupted) + "\n" + pending)
        androidx.compose.material3.TextButton(enabled = enabled, onClick = { confirm = true }) {
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(me.weishu.kernelsu.R.string.dashboard_clear_interruption))
        }
    }
    if (confirm) androidx.compose.material3.AlertDialog(onDismissRequest = { confirm = false },
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(me.weishu.kernelsu.R.string.dashboard_clear_interruption)) },
        text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(me.weishu.kernelsu.R.string.dashboard_batch_interrupted)) },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(android.R.string.cancel)) } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            confirm = false
            scope.launch {
                if (me.weishu.kernelsu.core.utils.ModuleMutationGate.tryAcquire()) {
                    try { if (withContext(Dispatchers.IO) { prefs.edit().remove("pending").commit() }) pending = null }
                    finally { me.weishu.kernelsu.core.utils.ModuleMutationGate.release() }
                }
            }
        }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(android.R.string.ok)) } })
}
