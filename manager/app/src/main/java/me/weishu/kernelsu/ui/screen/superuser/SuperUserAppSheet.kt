package me.weishu.kernelsu.ui.screen.superuser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.SuperUserException
import me.weishu.kernelsu.data.model.SuperUserFailure
import me.weishu.kernelsu.ui.component.AppIconImage

@Composable
internal fun SuperUserAppSheet(group: GroupedApps, state: SuperUserUiState, onDismiss: () -> Unit,
                               onGrantChange: (Boolean) -> Unit, onOpenProfile: () -> Unit) {
    val enabled = !state.isRefreshing && state.savingUid == null && state.error == null
    val known = state.error == null
    val colors = MaterialTheme.colorScheme
    val profile = group.primary.profile
    val grantDescription = stringResource(R.string.su_toggle_description, group.primary.label)
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        containerColor = colors.surfaceContainerLow) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.su_app_authorization), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = if (known && group.anyAllowSu) colors.primaryContainer else colors.surfaceContainerHigh,
                            contentColor = if (known && group.anyAllowSu) colors.onPrimaryContainer else colors.onSurface)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIconImage(packageInfo = group.primary.packageInfo, label = group.primary.label, modifier = Modifier.size(56.dp))
                            Text(group.primary.label, style = MaterialTheme.typography.headlineSmall)
                            SelectionContainer { Text(group.primary.packageName, style = MaterialTheme.typography.bodyMedium) }
                            Text(stringResource(when {
                                !known -> R.string.su_state_unknown
                                group.anyAllowSu -> R.string.app_profile_granted
                                else -> R.string.app_profile_not_granted
                            }), style = MaterialTheme.typography.titleMedium)
                            if (group.apps.size > 1) Text(stringResource(R.string.su_shared_explanation, group.apps.size))
                        }
                    }
                    if (state.error != null) Text(superUserFailureMessage(state.error), color = colors.error)
                    state.mutationError?.let { Text(superUserFailureMessage(it), color = colors.error) }
                    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                        ListItem(colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
                            content = { Text(stringResource(R.string.superuser)) },
                            supportingContent = { Text(stringResource(R.string.su_grant_explanation)) },
                            trailingContent = {
                                if (state.savingUid == group.uid) CircularProgressIndicator(Modifier.size(24.dp))
                                else Switch(checked = group.anyAllowSu, onCheckedChange = onGrantChange, enabled = enabled,
                                    modifier = Modifier.semantics { contentDescription = grantDescription })
                            })
                        ListItem(colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
                            content = { Text(stringResource(R.string.profile)) },
                            supportingContent = {
                                Text(when {
                                    profile == null || !known -> stringResource(R.string.su_state_unknown)
                                    group.anyAllowSu && profile.rootUseDefault -> stringResource(R.string.profile_default)
                                    group.anyAllowSu && profile.rootTemplate != null -> stringResource(R.string.su_template_name, profile.rootTemplate)
                                    !group.anyAllowSu && profile.nonRootUseDefault -> stringResource(R.string.profile_default)
                                    else -> stringResource(R.string.profile_custom)
                                })
                            })
                        ListItem(colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
                            content = { Text(stringResource(R.string.su_unmount_policy)) },
                            supportingContent = { Text(stringResource(when {
                                !known -> R.string.su_state_unknown
                                group.shouldUmount -> R.string.su_unmount_enabled
                                else -> R.string.su_unmount_disabled
                            })) })
                    }
                    Text(stringResource(R.string.su_identity), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                        SuperUserFact(stringResource(R.string.su_uid_user), "${group.uid} · ${group.uid / 100000}")
                        if (group.apps.size == 1) SuperUserFact(stringResource(R.string.su_version),
                            "${group.primary.packageInfo.versionName.orEmpty()} (${group.primary.packageInfo.longVersionCode})")
                        group.apps.firstNotNullOfOrNull { it.packageInfo.sharedUserId }?.let {
                            SuperUserFact(stringResource(R.string.su_shared_id), it)
                        }
                    }
                    if (group.apps.size > 1) {
                        Text(stringResource(R.string.su_affected_apps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                            group.apps.forEach { app ->
                                ListItem(colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
                                    content = { Text(app.label) }, supportingContent = { SelectionContainer { Text(app.packageName) } },
                                    leadingContent = { AppIconImage(packageInfo = app.packageInfo, label = app.label, modifier = Modifier.size(40.dp)) })
                            }
                        }
                    }
                }
                HorizontalDivider()
                Button(onClick = onOpenProfile, enabled = enabled, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.su_full_profile))
                }
            }
        }
    }
}

@Composable
private fun SuperUserFact(label: String, value: String) {
    ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = { Text(label, style = MaterialTheme.typography.labelLarge) },
        supportingContent = { SelectionContainer { Text(value) } })
}

/** Confirmation retains the complete group snapshot until it is checked again by the repository. */
@Composable
internal fun SuperUserGrantConfirmation(group: GroupedApps, enabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Shield, null) },
        title = { Text(stringResource(R.string.su_confirm_grant)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.su_grant_explanation))
                Text(stringResource(R.string.su_confirm_identity, group.uid, group.uid / 100000))
                if (group.apps.size > 1) Text(stringResource(R.string.su_shared_explanation, group.apps.size))
                group.apps.forEach { app ->
                    Text(app.label, style = MaterialTheme.typography.titleSmall)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled) { Text(stringResource(R.string.su_confirm_grant)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
internal fun superUserFailureMessage(error: Throwable): String = stringResource(when ((error as? SuperUserException)?.reason) {
    SuperUserFailure.Unavailable -> R.string.su_error_unavailable
    SuperUserFailure.DriverUnavailable -> R.string.su_error_driver
    SuperUserFailure.ManagerUnauthorized -> R.string.su_error_manager
    SuperUserFailure.InterfaceMismatch -> R.string.su_error_interface
    SuperUserFailure.RootUnavailable -> R.string.su_error_root
    SuperUserFailure.ServiceTimeout -> R.string.su_error_timeout
    SuperUserFailure.ServiceQueryFailed -> R.string.su_error_service
    SuperUserFailure.TargetChanged -> R.string.su_error_target_changed
    SuperUserFailure.PolicyChanged -> R.string.su_error_policy_changed
    SuperUserFailure.WriteFailed -> R.string.su_error_write
    SuperUserFailure.VerificationFailed -> R.string.su_error_verify
    SuperUserFailure.RestrictedUid -> R.string.su_error_uid
    SuperUserFailure.SepolicyFailed -> R.string.su_error_sepolicy
    SuperUserFailure.PartialWrite -> R.string.su_error_partial
    else -> R.string.su_error_read
})
