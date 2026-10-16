package me.weishu.kernelsu.ui.screen.appprofile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SuperUserRepositoryImpl
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.superuser.SuperUserGrantConfirmation
import me.weishu.kernelsu.ui.screen.superuser.superUserFailureMessage
import me.weishu.kernelsu.ui.util.forceStopApp
import me.weishu.kernelsu.ui.util.getSepolicy
import me.weishu.kernelsu.ui.util.launchApp
import me.weishu.kernelsu.ui.util.restartApp
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import me.weishu.kernelsu.ui.viewmodel.getTemplateInfoById

@Composable
fun AppProfileScreen(uid: Int) {
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val viewModel: SuperUserViewModel = viewModel()
    val repository = remember { SuperUserRepositoryImpl() }
    // Keep the exact identities reviewed on entry, including across rotation.
    val group = remember(uid) { SuperUserViewModel.getGroupedApp(uid) }
    if (group == null) {
        LaunchedEffect(uid) { navigator.pop() }
        return
    }
    val app = group.primary
    var target by rememberSaveable(uid) { mutableStateOf(group.authorizationTarget()) }
    var baseline by rememberSaveable(uid) { mutableStateOf<Natives.Profile?>(null) }
    var profile by rememberSaveable(uid) { mutableStateOf<Natives.Profile?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var saveError by remember { mutableStateOf<Throwable?>(null) }
    var discard by remember { mutableStateOf(false) }
    var grantConfirmation by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(uid, retry) {
        if (baseline != null) return@LaunchedEffect
        loading = true
        loadError = null
        val result = withContext(Dispatchers.IO) {
            repository.refreshProfiles(listOf(app)).mapCatching {
                val current = requireNotNull(it.single().profile)
                current.copy(rules = getSepolicy(current.rootTemplate ?: current.name))
            }
        }
        result.onSuccess { baseline = it; profile = it; target = target.copy(wasGranted = it.allowSu) }
            .onFailure { loadError = it }
        loading = false
    }
    val hasChanges = profile != baseline
    val requestBack = {
        if (!saving) {
            if (hasChanges) discard = true else navigator.pop()
        }
    }
    BackHandler { requestBack() }
    val savedMessage = stringResource(R.string.su_profile_saved)
    fun save() {
        val expected = baseline ?: return
        val proposal = profile ?: return
        if (saving || proposal == expected) return
        saving = true
        saveError = null
        scope.launch {
            try {
                repository.saveProfile(target, expected, proposal)
                    .onSuccess {
                        baseline = proposal
                        target = target.copy(wasGranted = proposal.allowSu)
                        viewModel.loadAppList(force = true)
                        scope.launch { snackbar.showSnackbar(savedMessage) }
                    }.onFailure { saveError = it }
            } finally { saving = false }
        }
    }
    val current = profile
    if (current == null) {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (loading || loadError == null) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.su_profile_loading))
                } else {
                    Text(superUserFailureMessage(loadError!!))
                    Button(onClick = { retry++ }) { Text(stringResource(R.string.su_retry)) }
                }
                TextButton(onClick = { navigator.pop() }) { Text(stringResource(android.R.string.cancel)) }
            }
        }
        return
    }
    val state = AppProfileUiState(uid, app.packageName, current, group,
        app.packageInfo.sharedUserId ?: group.apps.firstNotNullOfOrNull { it.packageInfo.sharedUserId }.orEmpty(),
        hasChanges = hasChanges, isSaving = saving, grantPending = current.allowSu != baseline?.allowSu)
    val actions = AppProfileActions(
        onBack = requestBack,
        onSave = {
            if (current.allowSu && baseline?.allowSu != true) grantConfirmation = true else save()
        },
        onLaunchApp = ::launchApp,
        onForceStopApp = ::forceStopApp,
        onRestartApp = ::restartApp,
        onViewTemplate = { id -> if (!saving) getTemplateInfoById(id)?.let { navigator.push(Route.TemplateEditor(it, true)) } },
        onManageTemplate = { if (!saving) navigator.push(Route.AppProfileTemplate) },
        onProfileChange = { if (!saving) profile = it },
    )
    AppProfileScreenMaterial(state, actions, snackbar)
    saveError?.let { error ->
        AlertDialog(onDismissRequest = { saveError = null },
            title = { Text(stringResource(R.string.su_save_failed)) },
            text = { Text(superUserFailureMessage(error)) },
            confirmButton = { TextButton(onClick = { saveError = null }) { Text(stringResource(android.R.string.ok)) } })
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text(stringResource(R.string.su_profile_unsaved)) },
        text = { Text(stringResource(R.string.su_profile_unsaved_body)) },
        confirmButton = { TextButton(onClick = { discard = false; navigator.pop() }) { Text(stringResource(R.string.su_profile_discard)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.su_profile_keep_editing)) } })
    if (grantConfirmation) SuperUserGrantConfirmation(group, enabled = !saving,
        onDismiss = { grantConfirmation = false }, onConfirm = { grantConfirmation = false; save() })
}
