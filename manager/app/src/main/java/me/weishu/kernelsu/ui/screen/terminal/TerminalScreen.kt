package me.weishu.kernelsu.ui.screen.terminal

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.toAnnotatedString

val TERMINAL_QUICK_COMMANDS = listOf(
    "ksud module list",
    "dmesg | tail -n 50",
    "getprop ro.build.fingerprint",
    "ls /data/adb",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<TerminalViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val back = dropUnlessResumed { viewModel.endSession(me.weishu.kernelsu.core.shell.SessionEndReason.LEFT_SCREEN); navigator.pop() }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    if (!state.warningAccepted) {
        AlertDialog(
            onDismissRequest = back,
            title = { Text(stringResource(R.string.terminal_warning_title)) },
            text = { Text(stringResource(R.string.terminal_warning_message)) },
            confirmButton = { TextButton(onClick = { viewModel.acceptWarning() }) { Text(stringResource(R.string.terminal_warning_accept)) } },
            dismissButton = { TextButton(onClick = back) { Text(stringResource(android.R.string.cancel)) } },
        )
    } else {
        LaunchedEffect(Unit) { viewModel.startSession() }
    }

    LaunchedEffect(state.lines.size, state.lines.lastOrNull()?.id) {
        if (state.lines.isNotEmpty()) listState.scrollToItem(state.lines.lastIndex)
    }

    fun submit() { viewModel.run(input); input = "" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.terminal_title))
                        Text(
                            stringResource(if (state.starting) R.string.terminal_status_starting else if (state.active) R.string.terminal_status_active else R.string.terminal_status_ended),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = viewModel::interrupt, enabled = state.active) {
                        Icon(Icons.Filled.Stop, stringResource(R.string.terminal_interrupt))
                    }
                    IconButton(onClick = viewModel::clear) { Icon(Icons.Filled.ClearAll, stringResource(R.string.terminal_clear)) }
                    IconButton(onClick = {
                        scope.launch {
                            val file = viewModel.exportLog()
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.terminal_export)))
                        }
                    }) { Icon(Icons.Filled.Share, stringResource(R.string.terminal_export)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            val stderrColor = MaterialTheme.colorScheme.error
            val systemColor = MaterialTheme.colorScheme.outline
            val inputColor = MaterialTheme.colorScheme.primary
            val defaultColor = MaterialTheme.colorScheme.onSurface
            SelectionContainer(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(state.lines, key = { it.id }) { line ->
                        val color = when (line.kind) {
                            TerminalLineKind.STDERR -> stderrColor
                            TerminalLineKind.SYSTEM -> systemColor
                            TerminalLineKind.INPUT -> inputColor
                            TerminalLineKind.STDOUT -> defaultColor
                        }
                        val text = remember(line.id, color) { line.spans.toAnnotatedString(color) }
                        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = true)
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TERMINAL_QUICK_COMMANDS.forEach { cmd ->
                    AssistChip(onClick = { viewModel.run(cmd) }, enabled = state.active,
                        label = { Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(me.weishu.kernelsu.core.shell.TempShellSession.MAX_COMMAND_CHARS) }, modifier = Modifier.weight(1f), singleLine = true,
                    enabled = state.active,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text(stringResource(R.string.terminal_input_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                )
                if (state.active) Button(onClick = ::submit, enabled = input.isNotBlank()) { Text(stringResource(R.string.terminal_run)) }
                else Button(onClick = viewModel::startSession, enabled = !state.starting) { Text(stringResource(R.string.terminal_restart)) }
            }
        }
    }
}
