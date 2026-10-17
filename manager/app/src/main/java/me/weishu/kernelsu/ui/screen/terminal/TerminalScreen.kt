package me.weishu.kernelsu.ui.screen.terminal

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.core.shell.SessionEndReason
import me.weishu.kernelsu.core.shell.TempShellSession
import me.weishu.kernelsu.data.terminal.*
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.service.TerminalTaskService
import me.weishu.kernelsu.ui.util.toAnnotatedString
import java.io.File

val TERMINAL_QUICK_COMMANDS = listOf("ksud module list", "dmesg | tail -n 50", "getprop ro.build.fingerprint", "ls /data/adb")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<TerminalViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by CommandFavorites.commands.collectAsStateWithLifecycle()
    val favoriteError by CommandFavorites.error.collectAsStateWithLifecycle()
    val limits by TaskLimitSettings.limits.collectAsStateWithLifecycle()
    val tasks by TerminalTaskRepository.tasks.collectAsStateWithLifecycle()
    val taskRequest by TerminalTaskNavigation.requests.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("terminal-ui", android.content.Context.MODE_PRIVATE) }
    var input by rememberSaveable { mutableStateOf("") }
    var pendingSubmission by rememberSaveable { mutableStateOf<String?>(null) }
    var submittedCommand by rememberSaveable { mutableStateOf("") }
    var background by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTask by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var favoriteName by rememberSaveable { mutableStateOf("") }
    var favoriteGroup by rememberSaveable { mutableStateOf("") }
    var favoriteCommand by rememberSaveable { mutableStateOf("") }
    var favoritePinned by rememberSaveable { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var font by rememberSaveable { mutableIntStateOf(preferences.getInt("font", 14).coerceIn(12, 20)) }
    var wrap by rememberSaveable { mutableStateOf(preferences.getBoolean("wrap", true)) }
    var following by rememberSaveable { mutableStateOf(true) }
    val list = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val back = { viewModel.endSession(SessionEndReason.LEFT_SCREEN); navigator.pop() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { TerminalTaskRepository.loadHistory() }.onFailure { withContext(Dispatchers.Main) { snackbar.showSnackbar(it.message ?: "Task history unavailable") } } } }
    LaunchedEffect(tasks, pendingSubmission) {
        if (pendingSubmission != null && tasks.any { it.id == pendingSubmission }) {
            if (input == submittedCommand) input = ""
            pendingSubmission = null
        }
    }
    LaunchedEffect(taskRequest) { if (taskRequest > 0) { panel = "tasks"; TerminalTaskNavigation.requests.compareAndSet(taskRequest, 0) } }
    LaunchedEffect(favoriteError) { favoriteError?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, forward) -> if (scrolling) following = !forward }
    }
    LaunchedEffect(state.lines.lastOrNull()?.id) { if (following && state.lines.isNotEmpty()) list.scrollToItem(state.lines.lastIndex) }
    fun insert(command: String) { input = command; background = false; panel = null }
    fun edit(favorite: CommandFavorite?) {
        editingId = favorite?.id; favoriteName = favorite?.name.orEmpty(); favoriteGroup = favorite?.group.orEmpty()
        favoriteCommand = favorite?.command ?: input; favoritePinned = favorite?.pinned ?: false
        formError = null; panel = "edit"
    }
    fun share(file: File) {
        scope.launch {
            runCatching {
                val export = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "task-exports").apply { mkdirs() }
                    dir.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - 86400000L }.forEach(File::delete)
                    File(dir, "terminal-${System.currentTimeMillis()}.log").also { file.copyTo(it, overwrite = true) }
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", export)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), context.getString(R.string.terminal_export)))
            }.onFailure { snackbar.showSnackbar(it.message ?: context.getString(R.string.terminal_task_failed)) }
        }
    }
    fun submit() {
        if (input.isBlank() || !state.active) return
        if (background) {
            runCatching {
                submittedCommand = input
                pendingSubmission = TerminalTaskService.submit(context, input)
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                panel = "tasks"
            }.onFailure { scope.launch { snackbar.showSnackbar(it.message ?: context.getString(R.string.terminal_task_failed)) } }
        } else { viewModel.run(input); input = ""; following = true }
    }
    if (!state.warningAccepted) AlertDialog(onDismissRequest = back,
        title = { Text(stringResource(R.string.terminal_warning_title)) },
        text = { Text(stringResource(R.string.terminal_warning_message)) },
        confirmButton = { TextButton(onClick = viewModel::acceptWarning) { Text(stringResource(R.string.terminal_warning_accept)) } },
        dismissButton = { TextButton(onClick = back) { Text(stringResource(android.R.string.cancel)) } })
    else LaunchedEffect(Unit) { viewModel.startSession() }

    Scaffold(topBar = {
        TopAppBar(title = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.terminal_title), style = MaterialTheme.typography.titleLarge)
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(14.dp))
                    Text(stringResource(if (state.starting) R.string.terminal_status_starting else if (state.active) R.string.terminal_session_ready else R.string.terminal_status_ended),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        } }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(android.R.string.cancel)) } },
            actions = {
                IconButton(onClick = viewModel::interrupt, enabled = state.active) { Icon(Icons.Filled.Stop, stringResource(R.string.terminal_interrupt)) }
                IconButton(onClick = { panel = "options" }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.terminal_more)) }
            })
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box {
                    SelectionContainer {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                            if (state.lines.isEmpty()) item {
                                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Filled.Terminal, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.terminal_empty_title), style = MaterialTheme.typography.headlineSmall)
                                    Text(stringResource(R.string.terminal_empty_body), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            items(state.lines, key = { it.id }) { line ->
                                val color = when (line.kind) {
                                    TerminalLineKind.STDERR -> MaterialTheme.colorScheme.error
                                    TerminalLineKind.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
                                    TerminalLineKind.INPUT -> MaterialTheme.colorScheme.primary
                                    TerminalLineKind.STDOUT -> MaterialTheme.colorScheme.onSurface
                                }
                                val text = remember(line.id, color) { line.spans.toAnnotatedString(color) }
                                val row = if (line.kind == TerminalLineKind.INPUT) Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small).padding(6.dp) else Modifier.padding(vertical = 1.dp)
                                Text(text, fontFamily = FontFamily.Monospace, fontSize = font.sp, softWrap = wrap,
                                    modifier = row.then(if (!wrap) Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier))
                            }
                        }
                    }
                    if (!following) SmallFloatingActionButton(onClick = { following = true; scope.launch { if (state.lines.isNotEmpty()) list.scrollToItem(state.lines.lastIndex) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.terminal_latest)) }
                }
            }
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { panel = "favorites" }) { Icon(Icons.Filled.Star, stringResource(R.string.terminal_favorites)) }
                        IconButton(onClick = { panel = "tasks" }) {
                            BadgedBox(badge = { val count = tasks.count { !it.status.terminal }; if (count > 0) Badge { Text(count.toString()) } }) {
                                Icon(Icons.Filled.PlaylistPlay, stringResource(R.string.terminal_tasks))
                            }
                        }
                        FilterChip(selected = background, onClick = { background = !background },
                            label = { Text(stringResource(if (background) R.string.terminal_background_mode else R.string.terminal_foreground_mode)) })
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { edit(null) }, enabled = input.isNotBlank()) { Icon(Icons.Filled.BookmarkAdd, stringResource(R.string.terminal_favorite_add)) }
                    }
                    val pins = favorites.filter { it.pinned }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        items(pins, key = { it.id }) { favorite -> SuggestionChip(onClick = { insert(favorite.command) }, label = { Text(favorite.name) }) }
                        item { SuggestionChip(onClick = { panel = "presets" }, label = { Text(stringResource(R.string.terminal_presets)) }) }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        TextField(value = input, onValueChange = { if (it.length <= TempShellSession.MAX_COMMAND_CHARS) input = it },
                            modifier = Modifier.weight(1f), minLines = 1, maxLines = 4, enabled = state.active,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            placeholder = { Text(stringResource(R.string.terminal_input_hint)) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default, autoCorrectEnabled = false))
                        if (state.active) FilledIconButton(onClick = ::submit, enabled = input.isNotBlank(), modifier = Modifier.padding(4.dp).size(48.dp)) {
                            Icon(Icons.Filled.PlayArrow, stringResource(R.string.terminal_run))
                        } else FilledIconButton(onClick = viewModel::startSession, enabled = !state.starting, modifier = Modifier.padding(4.dp).size(48.dp)) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.terminal_restart))
                        }
                    }
                    if (background) Text(stringResource(R.string.terminal_background_hint), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (panel != null) ModalBottomSheet(onDismissRequest = { panel = null }) {
        val title = when (panel) { "favorites", "edit" -> R.string.terminal_favorites; "tasks", "task-log" -> R.string.terminal_tasks; "presets" -> R.string.terminal_presets; else -> R.string.terminal_more }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { panel = null }) { Icon(Icons.Filled.Close, stringResource(android.R.string.cancel)) }
        }
        when (panel) {
            "options" -> Column(Modifier.padding(bottom = 24.dp)) {
                ListItem(content = { Text(stringResource(R.string.terminal_clear)) }, leadingContent = { Icon(Icons.Filled.ClearAll, null) }, modifier = Modifier.clickable { viewModel.clear(); panel = null })
                ListItem(content = { Text(stringResource(R.string.terminal_export)) }, leadingContent = { Icon(Icons.Filled.Share, null) }, modifier = Modifier.clickable { scope.launch { share(viewModel.exportLog()) }; panel = null })
                ListItem(content = { Text(stringResource(R.string.terminal_font, font)) }, trailingContent = { Row {
                    IconButton(onClick = { font = (font - 1).coerceAtLeast(12); preferences.edit().putInt("font", font).apply() }) { Icon(Icons.Filled.Remove, stringResource(R.string.terminal_smaller)) }
                    IconButton(onClick = { font = (font + 1).coerceAtMost(20); preferences.edit().putInt("font", font).apply() }) { Icon(Icons.Filled.Add, stringResource(R.string.terminal_larger)) }
                } })
                ListItem(content = { Text(stringResource(R.string.terminal_wrap)) }, trailingContent = { Switch(wrap, { wrap = it; preferences.edit().putBoolean("wrap", it).apply() }) })
                ListItem(content = { Text(stringResource(R.string.terminal_limits)) }, modifier = Modifier.clickable { panel = "limits" })
                ListItem(content = { Text(stringResource(R.string.terminal_end_session)) }, modifier = Modifier.clickable { viewModel.endSession(); panel = null })
            }
            "limits" -> Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.terminal_limits_note), style = MaterialTheme.typography.bodySmall)
                @Composable fun control(label: String, value: Int, range: IntRange, update: (Int) -> TaskLimits) {
                    Text("$label: $value", style = MaterialTheme.typography.titleSmall)
                    Slider(value.toFloat(), { TaskLimitSettings.save(update(it.toInt())) },
                        valueRange = range.first.toFloat()..range.last.toFloat(), steps = (range.last - range.first - 1).coerceAtLeast(0),
                        onValueChangeFinished = { scope.launch(Dispatchers.IO) { TerminalTaskRepository.applyLimits() } })
                }
                control(stringResource(R.string.terminal_limits_queue), limits.queued, 1..16) { limits.copy(queued = it) }
                control(stringResource(R.string.terminal_limits_records), limits.records, 10..100) { limits.copy(records = it) }
                control(stringResource(R.string.terminal_limits_days), limits.days, 1..30) { limits.copy(days = it) }
                control(stringResource(R.string.terminal_limits_per_log), limits.logMiB, 1..2) { limits.copy(logMiB = it) }
                control(stringResource(R.string.terminal_limits_total_log), limits.totalMiB, 5..20) { limits.copy(totalMiB = it) }
            }
            "presets" -> Column(Modifier.padding(bottom = 24.dp)) {
                val labels = listOf(R.string.terminal_preset_modules, R.string.terminal_preset_kernel, R.string.terminal_preset_device, R.string.terminal_preset_files)
                TERMINAL_QUICK_COMMANDS.forEachIndexed { index, command -> ListItem(content = { Text(stringResource(labels[index])) },
                    supportingContent = { Text(command, fontFamily = FontFamily.Monospace) }, modifier = Modifier.clickable { insert(command) }) }
            }
            "favorites" -> {
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.terminal_favorite_search)) })
                    IconButton(onClick = { edit(null) }) { Icon(Icons.Filled.Add, stringResource(R.string.terminal_favorite_add)) }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    val visible = favorites.filter { it.name.contains(query, true) || it.group.contains(query, true) || it.command.contains(query, true) }
                    if (visible.isEmpty()) item { Text(stringResource(R.string.terminal_favorite_empty), Modifier.padding(24.dp)) }
                    items(visible, key = { it.id }) { favorite -> ListItem(content = { Text(favorite.name) }, supportingContent = {
                        Text(listOf(favorite.group, favorite.command).filter(String::isNotBlank).joinToString("\n"), maxLines = 2, fontFamily = FontFamily.Monospace)
                    }, leadingContent = { Icon(if (favorite.pinned) Icons.Filled.PushPin else Icons.Filled.StarOutline, null) },
                        trailingContent = { IconButton(onClick = { edit(favorite) }) { Icon(Icons.Filled.Edit, stringResource(R.string.terminal_favorite_edit)) } },
                        modifier = Modifier.clickable { insert(favorite.command) }) }
                }
            }
            "edit" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(favoriteName, { if (it.length <= 64) favoriteName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.terminal_favorite_name)) })
                OutlinedTextField(favoriteGroup, { if (it.length <= 64) favoriteGroup = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.terminal_favorite_group)) })
                OutlinedTextField(favoriteCommand, { if (it.length <= 16384) favoriteCommand = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    label = { Text(stringResource(R.string.terminal_input_hint)) }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                ListItem(content = { Text(stringResource(R.string.terminal_favorite_pin)) }, trailingContent = { Switch(favoritePinned, { favoritePinned = it }) })
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val favorite = CommandFavorite(id = editingId ?: java.util.UUID.randomUUID().toString(), name = favoriteName, command = favoriteCommand, group = favoriteGroup, pinned = favoritePinned)
                        runCatching { FavoritePolicy.validate(favorites.filterNot { it.id == favorite.id } + favorite); CommandFavorites.save(favorite); panel = "favorites" }.onFailure { formError = it.message }
                    }) { Text(stringResource(R.string.terminal_favorite_save)) }
                    editingId?.let { id ->
                        TextButton(onClick = { CommandFavorites.remove(id); panel = "favorites" }) { Text(stringResource(R.string.terminal_favorite_delete)) }
                        IconButton(onClick = { CommandFavorites.move(id, -1) }) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.terminal_favorite_up)) }
                        IconButton(onClick = { CommandFavorites.move(id, 1) }) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.terminal_favorite_down)) }
                    }
                }
            }
            "tasks" -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { TextButton(onClick = { panel = "limits" }) { Text(stringResource(R.string.terminal_limits)) } }
                if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() && tasks.any { !it.status.terminal }) {
                    item { Text(stringResource(R.string.terminal_notifications_off), Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall) }
                }
                if (tasks.isEmpty()) item { Text(stringResource(R.string.terminal_tasks_empty), Modifier.padding(24.dp)) }
                items(tasks, key = { it.id }) { task -> ListItem(content = { Text(task.name) }, supportingContent = {
                    Text(taskStatusLabel(task.status) + (task.code?.let { " · ${stringResource(R.string.terminal_task_exit, it)}" } ?: ""))
                }, leadingContent = { Icon(if (task.status == TaskStatus.COMPLETED) Icons.Filled.CheckCircle else Icons.Filled.Terminal, null) },
                    trailingContent = {
                        if (!task.status.terminal) IconButton(onClick = { TerminalTaskService.stop(context, task.id) }) { Icon(Icons.Filled.Stop, stringResource(R.string.terminal_task_stop)) }
                    }, modifier = Modifier.clickable { selectedTask = task.id; panel = "task-log" }) }
            }
            "task-log" -> selectedTask?.let { id ->
                val task = tasks.firstOrNull { it.id == id }
                var log by remember(id) { mutableStateOf("") }
                var tick by remember(id) { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(id, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) {
                    log = withContext(Dispatchers.IO) { runCatching { TerminalTaskRepository.tail(id) }.getOrElse { it.message.orEmpty() } }; tick = android.os.SystemClock.elapsedRealtime(); delay(500)
                } } }
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    task?.let { Text(taskStatusLabel(it.status), style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(it.command, fontFamily = FontFamily.Monospace) }
                        if (it.started > 0) Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.started)), style = MaterialTheme.typography.bodySmall)
                        val elapsed = if (!it.status.terminal && it.startedElapsed > 0) (tick - it.startedElapsed).coerceAtLeast(0) else it.elapsed
                        if (elapsed > 0) Text(stringResource(R.string.terminal_task_elapsed, elapsed / 1000))
                        it.signal?.let { signal -> Text(stringResource(R.string.terminal_task_signal, signal)) }
                        it.code?.let { code -> Text(stringResource(R.string.terminal_task_exit, code)) }
                        it.error?.let { error -> SelectionContainer { Text(error, color = MaterialTheme.colorScheme.error) } }
                    }
                    SelectionContainer { Text(log, Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = font.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { share(TerminalTaskRepository.logFile(id)) }) { Text(stringResource(R.string.terminal_export)) }
                        if (task?.status?.terminal == true) {
                            TextButton(onClick = { insert(task.command) }) { Text(stringResource(R.string.terminal_task_rerun)) }
                            TextButton(onClick = { scope.launch(Dispatchers.IO) { TerminalTaskRepository.remove(id) }; panel = "tasks" }) { Text(stringResource(R.string.terminal_task_remove)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun taskStatusLabel(status: TaskStatus) = stringResource(when (status) {
    TaskStatus.QUEUED -> R.string.terminal_task_queued
    TaskStatus.STARTING -> R.string.terminal_status_starting
    TaskStatus.RUNNING -> R.string.terminal_task_running
    TaskStatus.STOP_REQUESTED -> R.string.terminal_task_stopping
    TaskStatus.COMPLETED -> R.string.terminal_task_completed
    TaskStatus.FAILED -> R.string.terminal_task_failed
    TaskStatus.CANCELED -> R.string.terminal_task_canceled
    TaskStatus.INTERRUPTED -> R.string.terminal_task_interrupted
})
