package me.weishu.kernelsu.ui.screen.experimentalloader

import android.util.AtomicFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.ExperimentalLoaderSettings
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import java.io.File

@Composable
fun ExperimentalLoaderSettingsScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val file = remember { AtomicFile(File(context.filesDir, "experimental-loader/preferences.conf")) }
    var draft by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<ExperimentalLoaderSettings, String>(
            save = { it.serialize() }, restore = { ExperimentalLoaderSettings.parse(it.toByteArray()) }),
    ) { mutableStateOf(ExperimentalLoaderSettings()) }
    var savedGeneration by androidx.compose.runtime.saveable.rememberSaveable { mutableLongStateOf(0) }
    var ready by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var recoveryRequested by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { runCatching {
            if ((file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())) ExperimentalLoaderSettings.parse(file.openRead().use { it.readNBytes(16385) })
                else ExperimentalLoaderSettings()
        } }
        result.onSuccess { draft = it; savedGeneration = it.generation; ready = true }
            .onFailure { message = it.message; ready = true }
    }
    fun set(key: String, value: String) { draft = draft.copy(overrides = draft.overrides + (key to value)) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(16385) }
                    ?: error("Cannot read preferences")
                ExperimentalLoaderSettings.parse(bytes)
            } }.onSuccess { draft = it.copy(generation = savedGeneration); message = context.getString(R.string.loader_preferences_imported) }
                .onFailure { message = it.message }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val snapshot = draft.serialize()
            scope.launch { runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(snapshot.toByteArray()) }
                    ?: error("Cannot write preferences")
            } }.onSuccess { message = context.getString(R.string.loader_preferences_exported) }
                .onFailure { message = it.message } }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false },
        title = { Text(stringResource(R.string.loader_preferences_reset)) },
        text = { Text(stringResource(R.string.loader_preferences_only)) },
        confirmButton = { TextButton(onClick = { draft = ExperimentalLoaderSettings(generation = savedGeneration); recoveryRequested = true; reset = false }) {
            Text(stringResource(R.string.loader_preferences_reset))
        } }, dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(R.string.module_sheet_cancel)) } })
    val effective = draft.effective()
    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.loader_preferences_title)) },
            navigationIcon = { IconButton(onClick = { navigator.pop() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.module_sheet_back))
            } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Husky Loader 0.2", style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.loader_preferences_gate))
                    Text("Pixel 8 Pro · ZP11.260918.007 · 4K")
                    Text(stringResource(R.string.loader_preferences_only))
                } }
                message?.let { Text(it) }
                if (!ready) LoadingIndicator()
            }
            item {
                Text(stringResource(R.string.loader_preferences_preset), style = MaterialTheme.typography.titleLarge)
                listOf("balanced", "lean", "diagnostic").forEach { name ->
                    ListItem(modifier = Modifier.clickable(enabled = ready && !saving) { draft = draft.withPreset(name) },
                        headlineContent = { Text(stringResource(when(name) { "balanced" -> R.string.loader_preferences_balanced; "lean" -> R.string.loader_preferences_lean; else -> R.string.loader_preferences_diagnostic })) }, trailingContent = { RadioButton(selected = draft.preset == name,
                            enabled = ready && !saving, onClick = { draft = draft.withPreset(name) }) })
                }
            }
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.loader_preferences_capture)) },
                    trailingContent = { Switch(effective["kernel_log"] == "true", enabled = ready && !saving,
                        onCheckedChange = { set("kernel_log", it.toString()) }) })
                Text(stringResource(R.string.loader_preferences_buffer), style = MaterialTheme.typography.titleMedium)
                if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                    listOf("8", "16", "32", "64").forEach { value ->
                        ListItem(headlineContent = { Text("$value KiB") }, trailingContent = {
                            RadioButton(selected = effective["io_buffer_kib"] == value, enabled = ready && !saving,
                                onClick = { set("io_buffer_kib", value) })
                        })
                    }
                } else SingleChoiceSegmentedButtonRow {
                    listOf("8", "16", "32", "64").forEachIndexed { index, value ->
                        SegmentedButton(selected = effective["io_buffer_kib"] == value,
                            onClick = { set("io_buffer_kib", value) }, enabled = ready && !saving,
                            shape = SegmentedButtonDefaults.itemShape(index, 4)) { Text("$value KiB") }
                    }
                }
            }
            item {
                Text(stringResource(R.string.loader_preferences_output), style = MaterialTheme.typography.titleMedium)
                listOf("json", "text").forEach { value ->
                    ListItem(headlineContent = { Text(if (value == "json") "JSON" else stringResource(R.string.loader_preferences_text)) },
                        trailingContent = { RadioButton(selected = effective["output"] == value, enabled = ready && !saving,
                            onClick = { set("output", value) }) })
                }
                Text(stringResource(R.string.loader_preferences_verbosity), style = MaterialTheme.typography.titleMedium)
                listOf("error", "normal", "debug").forEach { value ->
                    ListItem(headlineContent = { Text(stringResource(when(value) { "error" -> R.string.loader_preferences_error; "normal" -> R.string.loader_preferences_normal; else -> R.string.loader_preferences_debug })) },
                        trailingContent = { RadioButton(selected = effective["verbosity"] == value, enabled = ready && !saving,
                            onClick = { set("verbosity", value) }) })
                }
                Text(stringResource(R.string.loader_preferences_log_limit, effective["log_limit_kib"]!!.toInt()))
                Slider(value = effective["log_limit_kib"]!!.toFloat(), onValueChange = { set("log_limit_kib", it.toInt().toString()) },
                    valueRange = 4f..64f, steps = 59, enabled = ready && !saving)
                Text(stringResource(R.string.loader_preferences_input_limit, effective["module_limit_mib"]!!.toInt()))
                Slider(value = effective["module_limit_mib"]!!.toFloat(), onValueChange = { set("module_limit_mib", it.toInt().toString()) },
                    valueRange = 1f..16f, steps = 14, enabled = ready && !saving)
                Text(stringResource(R.string.loader_preferences_deadline, effective["deadline_seconds"]!!.toInt()))
                Slider(value = effective["deadline_seconds"]!!.toFloat(), onValueChange = { set("deadline_seconds", it.toInt().toString()) },
                    valueRange = 1f..30f, steps = 28, enabled = ready && !saving)
            }
            item {
                Text(stringResource(R.string.loader_preferences_effective), style = MaterialTheme.typography.titleLarge)
                effective.toSortedMap().forEach { (key, value) ->
                    val label = when(key) {
                        "output" -> R.string.loader_preferences_output
                        "verbosity" -> R.string.loader_preferences_verbosity
                        "kernel_log" -> R.string.loader_preferences_capture
                        "log_limit_kib" -> R.string.loader_preferences_log
                        "history_count" -> R.string.loader_preferences_history
                        "io_buffer_kib" -> R.string.loader_preferences_buffer
                        "module_limit_mib" -> R.string.loader_preferences_input
                        "cache_enabled" -> R.string.loader_preferences_cache
                        else -> R.string.loader_preferences_timeout
                    }
                    ListItem(headlineContent = { Text(stringResource(label)) }, supportingContent = {
                        Text("$value · " + stringResource(if (key in draft.overrides) R.string.loader_preferences_custom else R.string.loader_preferences_default))
                    })
                }
                Button(shapes = ButtonDefaults.shapes(), enabled = ready && !saving,
                    modifier = Modifier.fillMaxWidth(), onClick = {
                        val snapshot = draft.copy(generation = savedGeneration + 1)
                        saving = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                val current = if ((file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())) runCatching { ExperimentalLoaderSettings.parse(file.openRead().use { it.readNBytes(16385) }).generation }
                                    .getOrElse { if (recoveryRequested) savedGeneration else throw it } else 0
                                check(current == savedGeneration) { "Stale settings; reopen this screen before saving" }
                                ExperimentalLoaderSettings.parse(snapshot.serialize().toByteArray())
                                val output = file.startWrite()
                                try { output.write(snapshot.serialize().toByteArray()); file.finishWrite(output) }
                                catch (error: Exception) { file.failWrite(output); throw error }
                            } }.onSuccess { draft = snapshot; savedGeneration = snapshot.generation; recoveryRequested = false; message = context.getString(R.string.loader_preferences_saved) }
                                .onFailure { message = it.message }
                            saving = false
                        }
                    }) { Text(stringResource(R.string.loader_preferences_save)) }
                TextButton(onClick = { import.launch(arrayOf("text/*", "application/octet-stream")) }, enabled = ready && !saving) {
                    Text(stringResource(R.string.loader_preferences_import))
                }
                TextButton(onClick = { export.launch("husky-loader-settings.conf") }, enabled = ready && !saving) {
                    Text(stringResource(R.string.loader_preferences_export))
                }
                TextButton(onClick = { reset = true }, enabled = ready && !saving) { Text(stringResource(R.string.loader_preferences_reset)) }
            }
        }
    }
}
