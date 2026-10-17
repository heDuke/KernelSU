package me.weishu.kernelsu.ui.component.module

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.RemoteModuleCandidate
import me.weishu.kernelsu.data.repository.RemoteModuleResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModuleInstallSourceSheet(onDismiss: () -> Unit, onLocal: () -> Unit) {
    var remote by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<RemoteModuleCandidate>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheet = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    fun closeThen(action: () -> Unit) { scope.launch { sheet.hide(); onDismiss(); action() } }
    ModalBottomSheet(sheetState = sheet, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(stringResource(if (remote) R.string.module_install_remote else R.string.module_install_source), Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
            if (!remote) {
                ListItem(content = { Text(stringResource(R.string.module_install_remote)) }, supportingContent = { Text(stringResource(R.string.module_install_remote_hint)) },
                    leadingContent = { Icon(Icons.Filled.Link, null) }, modifier = Modifier.clickable { remote = true })
                ListItem(content = { Text(stringResource(R.string.module_install_local)) }, leadingContent = { Icon(Icons.Filled.FolderOpen, null) }, modifier = Modifier.clickable { closeThen(onLocal) })
            } else {
                OutlinedTextField(url, { url = it; error = null; candidates = emptyList() }, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    enabled = !busy, label = { Text(stringResource(R.string.module_install_remote_url)) }, singleLine = true)
                Button(onClick = { busy = true; scope.launch {
                    runCatching { RemoteModuleResolver.resolve(url) }.onSuccess { candidates = it }.onFailure { error = it.message }
                    busy = false
                } }, enabled = !busy && url.isNotBlank(), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { Text(stringResource(R.string.module_install_discover)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                error?.let { Text(it, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(candidates, key = { it.url }) { candidate -> ListItem(content = { Text(candidate.name) },
                        supportingContent = { Text(listOf(candidate.version, candidate.size.takeIf { it > 0 }?.let { android.text.format.Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current, it) }.orEmpty()).filter(String::isNotBlank).joinToString(" · ")) },
                        modifier = Modifier.clickable { closeThen { ModuleInstallController.review(listOf(ModuleInstallTarget(
                            name = candidate.name, downloadUrl = candidate.url, size = candidate.size, moduleId = candidate.id, version = candidate.version, source = url))) } }) }
                }
            }
        }
    }
}
