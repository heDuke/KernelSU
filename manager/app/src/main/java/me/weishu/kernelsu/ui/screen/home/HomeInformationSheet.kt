package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R

/** Domain composition of native sheet, app actions and information; no custom gestures. */
@Composable
internal fun HomeInformationSheet(identity: Any, title: String, onDismiss: () -> Unit,
                                  onReport: (() -> Unit)? = null,
                                  footer: (@Composable () -> Unit)? = null,
                                  dismissEnabled: Boolean = true,
                                  content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = { if (dismissEnabled) onDismiss() }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                var reportMenu by remember(identity) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    if (onReport != null) Box {
                        IconButton(onClick = { reportMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.home_sheet_report_menu)) }
                        DropdownMenu(expanded = reportMenu, onDismissRequest = { reportMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.dashboard_export)) }, onClick = { reportMenu = false; onReport() })
                        }
                    }
                    IconButton(enabled = dismissEnabled, onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                }
                key(identity) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                }
                if (footer != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { footer() }
                }
            }
        }
    }
}

internal enum class HomeTone { Primary, Secondary, Neutral, Error }

@Composable
internal fun HomeStateCard(value: String, explanation: String, icon: ImageVector, tone: HomeTone = HomeTone.Primary) {
    val colors = MaterialTheme.colorScheme
    val (container, foreground) = when (tone) {
        HomeTone.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        HomeTone.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
        HomeTone.Neutral -> colors.surfaceContainerHigh to colors.onSurface
        HomeTone.Error -> colors.errorContainer to colors.onErrorContainer
    }
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(32.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (explanation.isNotBlank()) Text(explanation, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun HomeFacts(title: String, facts: List<Pair<String, String>>) {
    if (facts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            facts.forEach { (label, value) ->
                ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer), content = { Text(label, style = MaterialTheme.typography.labelLarge) }, supportingContent = {
                    SelectionContainer { Text(value.ifBlank { stringResource(R.string.dashboard_unknown) }, style = MaterialTheme.typography.bodyLarge) }
                })
            }
        }
    }
}

@Composable
internal fun HomeTechnicalDetails(identity: Any, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember(identity) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(if (expanded) R.string.home_sheet_hide_details else R.string.home_sheet_technical_details))
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
