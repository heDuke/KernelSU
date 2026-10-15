package me.weishu.kernelsu.ui.screen.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.getDefaultPartition
import me.weishu.kernelsu.ui.util.getRootShell
import me.weishu.kernelsu.ui.util.getSlotSuffix
import me.weishu.kernelsu.ui.util.rootAvailable
import java.io.File
import java.security.MessageDigest

private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

@Composable
internal fun HomeBackupPanel(enabled: Boolean) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val fallback = File(context.createDeviceProtectedStorageContext().dataDir, "boot_backup").absolutePath
    val roots = listOf("/data/adb/ksu", fallback)
    var files by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val result = getRootShell().newJob().add(roots.joinToString("\n") {
                    "if [ -d ${quote(it)} ]; then find ${quote(it)} -maxdepth 1 -type f -name 'ksu_backup_*' -print | head -n 33; fi"
                }).exec()
                check(result.isSuccess) { "Cannot inspect backup directories" }
                result.out.filter { path -> roots.any { parent -> path.matches(Regex(Regex.escape(parent) + "/ksu_backup_[0-9a-f]{40}")) } }.distinct().take(32)
            }
        }.fold(onSuccess = { files = it }, onFailure = { error = it.message })
        target = withContext(Dispatchers.IO) { runCatching { getDefaultPartition() + getSlotSuffix(false) }.getOrNull() }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val path = selected
        if (uri != null && path != null && !busy) scope.launch {
            busy = true; error = null
            val result = withContext(Dispatchers.IO) { runCatching {
                require(roots.any { parent -> path.matches(Regex(Regex.escape(parent) + "/ksu_backup_[0-9a-f]{40}")) })
                check(Natives.isManager && rootAvailable())
                val file = File.createTempFile("backup-export-", ".img", context.cacheDir)
                try {
                    val stat = getRootShell().newJob().add("test -f ${quote(path)} && test ! -L ${quote(path)} && stat -c '%s' ${quote(path)}").exec()
                    val size = stat.out.singleOrNull()?.trim()?.toLongOrNull()
                    require(stat.isSuccess && size != null && size in 1..536_870_912) { "Unreadable or oversized backup" }
                    check(getRootShell().newJob().add("head -c ${size + 1} ${quote(path)} > ${quote(file.absolutePath)}").exec().isSuccess)
                    require(file.length() == size)
                    val digest = MessageDigest.getInstance("SHA-1")
                    file.inputStream().use { input -> val bytes = ByteArray(16_384); while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) } }
                    require(digest.digest().joinToString("") { "%02x".format(it) } == path.substringAfterLast('_')) { "Backup digest mismatch" }
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output, 16_384) } }
                } finally { file.delete() }
            } }
            result.exceptionOrNull()?.let { error = it.message }
            busy = false
        }
    }
    if (!enabled) Text(stringResource(R.string.dashboard_unavailable))
    else if (files == null && error == null) LoadingIndicator()
    else {
        if (files.isNullOrEmpty()) Text(stringResource(R.string.dashboard_no_readable_backup))
        files.orEmpty().forEach { path ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DashboardInfo(stringResource(R.string.dashboard_backup), path.substringAfterLast('/'))
                TextButton(enabled = !busy, onClick = { selected = path; export.launch(path.substringAfterLast('/') + ".img") }) { Text(stringResource(R.string.dashboard_export)) }
            }
        }
        Text(stringResource(R.string.dashboard_backup_limit), style = MaterialTheme.typography.bodySmall)
        DashboardInfo(stringResource(R.string.dashboard_restore_target), target.orEmpty())
        Button(enabled = !busy && !files.isNullOrEmpty() && !target.isNullOrBlank(), onClick = { restore = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.dashboard_restore_current)) }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (restore) AlertDialog(onDismissRequest = { restore = false }, title = { Text(stringResource(R.string.dashboard_restore_current)) },
        text = { Text(stringResource(R.string.dashboard_backup_note) + "\n" + target.orEmpty() + "\n" + stringResource(R.string.dashboard_install_warning)) },
        dismissButton = { TextButton(onClick = { restore = false }) { Text(stringResource(android.R.string.cancel)) } },
        confirmButton = { TextButton(onClick = {
            restore = false
            scope.launch {
                val authorized = withContext(Dispatchers.IO) { Natives.isManager && rootAvailable() && !Natives.isSafeMode }
                if (authorized) navigator.push(Route.Flash(FlashIt.FlashRestore)) else error = context.getString(R.string.dashboard_unavailable)
            }
        }) { Text(stringResource(R.string.dashboard_confirm_write)) } })
}
