package me.weishu.kernelsu.ui.screen.module

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.data.model.ModuleUpdateInfo
import me.weishu.kernelsu.data.model.RecommendedModule
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.ui.component.ObserveAsEvents
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressiveNoticeCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import androidx.compose.material3.Scaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveSectionTitle
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopup
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.util.reboot

@SuppressLint("StringFormatInvalid")
@Composable
fun ModulePagerMaterial(
    uiState: ModuleUiState,
    confirmDialogState: ModuleConfirmDialogState?,
    moduleEvent: Flow<ModuleEffect>,
    actions: ModuleActions,
    bottomInnerPadding: Dp,
) {
    val snackBarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    val context = LocalContext.current
    val resource = LocalResources.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val pullToRefreshState = rememberPullToRefreshState()

    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val threshold = with(LocalDensity.current) { 100.dp.toPx() }
    val fabExpanded by remember {
        var lastIndex = 0
        var lastOffset = 0
        var scrollDelta = 0f
        var expanded = true
        derivedStateOf {
            val currentIndex = listState.firstVisibleItemIndex
            val currentOffset = listState.firstVisibleItemScrollOffset
            val delta = if (currentIndex == lastIndex) {
                (currentOffset - lastOffset).toFloat()
            } else if (currentIndex > lastIndex) {
                100f
            } else {
                -100f
            }
            scrollDelta = (scrollDelta + delta).coerceIn(-threshold, threshold)
            lastIndex = currentIndex
            lastOffset = currentOffset
            if (currentIndex == 0) {
                expanded = true
                scrollDelta = 0f
            } else if (expanded && scrollDelta >= threshold) {
                expanded = false
                scrollDelta = 0f
            } else if (!expanded && scrollDelta <= -threshold) {
                expanded = true
                scrollDelta = 0f
            }
            expanded
        }
    }

    val shortcutState = rememberModuleShortcutState(context)
    val showShortcutDialog = remember { mutableStateOf(false) }
    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            when (val request = confirmDialogState?.request) {
                is ModuleConfirmRequest.Uninstall -> actions.onUninstallModule(request.module)
                is ModuleConfirmRequest.Update -> actions.onConfirmUpdate(request)
                null -> Unit
            }
        },
        onDismiss = actions.onDismissConfirmRequest,
    )

    fun openShortcutDialogForType(type: ShortcutType) {
        shortcutState.selectType(type)
        showShortcutDialog.value = true
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutState.updateIconUri(uri?.toString())
    }

    fun onModuleAddShortcut(module: Module, type: ShortcutType) {
        shortcutState.bindModule(module)
        openShortcutDialogForType(type)
    }

    LaunchedEffect(confirmDialogState) {
        confirmDialogState?.let {
            confirmDialog.showConfirm(
                title = it.title,
                content = it.content,
                markdown = it.markdown,
                html = it.html,
                confirm = it.confirm,
                dismiss = it.dismiss,
            )
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarJob = remember { mutableStateOf<Job?>(null) }
    ObserveAsEvents(moduleEvent) { event ->
        when (event) {
            is ModuleEffect.Toast -> {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }

            is ModuleEffect.SnackBar -> {
                // Cancel the previous reboot snackbar so a new one replaces it instead of queueing
                snackbarJob.value?.cancel()
                snackBarHost.currentSnackbarData?.dismiss()
                // Soft reboot keeps the jailbreak and still applies module changes
                val softReboot = isSoftRebootPreferred()
                snackbarJob.value = scope.launch {
                    val result = snackBarHost.showSnackbar(
                        message = event.message,
                        actionLabel = resource.getString(if (softReboot) R.string.reboot_soft else R.string.reboot),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        reboot(if (softReboot) "soft_reboot" else "")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.module)) },
                searchText = uiState.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                snackbarHostState = snackBarHost,
                actions = {
                    RebootListPopup()

                    var showDropdown by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showDropdown = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )
                        androidx.compose.material3.DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_sort_action_first)) },
                                leadingIcon = { if (uiState.sortActionFirst) Icon(Icons.Filled.Check, null) },
                                onClick = { showDropdown = false; actions.onToggleSortActionFirst() })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_sort_enabled_first)) },
                                leadingIcon = { if (uiState.sortEnabledFirst) Icon(Icons.Filled.Check, null) },
                                onClick = { showDropdown = false; actions.onToggleSortEnabledFirst() })
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                searchContent = { bottomPadding, closeSearch ->
                    val latestSearchResults = rememberUpdatedState(uiState.searchResults)
                    ScrollToTopOnChange(
                        searchListState,
                        uiState.searchStatus.searchText,
                    ) { latestSearchResults.value }
                    ModuleList(
                        bottomInnerPadding = bottomPadding,
                        modifier = Modifier.fillMaxSize(),
                        listState = searchListState,
                        displayModules = uiState.searchResults,
                        updateInfoMap = uiState.updateInfo,
                        actions = actions,
                        onClickModule = { module ->
                            if (module.hasWebUi) {
                                actions.onOpenWebUi(module)
                                closeSearch()
                            }
                        },
                        onModuleAddShortcut = { module, type -> onModuleAddShortcut(module, type) },
                        closeSearch = closeSearch,
                    )
                }
            )
        },
        floatingActionButton = {
            if (uiState.installButtonVisible) {
                val moduleInstall = stringResource(id = R.string.module_install)
                val selectZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { activityResult ->
                    if (activityResult.resultCode != RESULT_OK) {
                        return@rememberLauncherForActivityResult
                    }
                    val data = activityResult.data ?: return@rememberLauncherForActivityResult
                    val clipData = data.clipData

                    val uris = mutableListOf<Uri>()
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                        }
                    } else {
                        data.data?.let { uris.add(it) }
                    }

                    actions.onOpenFlash(uris)
                }

                var chooseSource by remember { mutableStateOf(false) }
                SmallExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = bottomInnerPadding), expanded = fabExpanded,
                    onClick = { chooseSource = true },
                    icon = { Icon(Icons.Filled.Add, moduleInstall) }, text = { Text(moduleInstall) })
                if (chooseSource) me.weishu.kernelsu.ui.component.module.ModuleInstallSourceSheet(
                    onDismiss = { chooseSource = false }, onLocal = {
                        selectZipLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        })
                    })
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = {
            SnackBarHost(hostState = snackBarHost, modifier = Modifier.let {
                if (!uiState.installButtonVisible) it.padding(
                    bottom =
                        bottomInnerPadding
                ) else it
            })
        }
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            if (uiState.magiskInstalled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.module_magisk_conflict),
                        textAlign = TextAlign.Center,
                    )
                }
                return@PullToRefreshBox
            }
            val latestModuleList = rememberUpdatedState(uiState.moduleList)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                uiState.sortEnabledFirst,
                uiState.sortActionFirst,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestModuleList.value }
            ModuleList(
                bottomInnerPadding = bottomInnerPadding,
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                listState = listState,
                displayModules = uiState.moduleList,
                recommendedModules = emptyList(),
                pendingItems = uiState.pendingItems,
                selectedPendingIds = uiState.selectedPendingIds,
                isBatchProcessing = uiState.isBatchProcessing,
                showPendingCard = true,
                updateInfoMap = uiState.updateInfo,
                actions = actions,
                installRecommendedEnabled = uiState.installButtonVisible && !uiState.isBatchProcessing,
                mutationsBlockedReason = uiState.interfaceBlockedReason,
                showEmptyPlaceholder = true,
                onClickModule = { module ->
                    if (module.hasWebUi) {
                        actions.onOpenWebUi(module)
                    }
                },
                onModuleAddShortcut = { module, type -> onModuleAddShortcut(module, type) },
            )
        }
    }

    ModuleShortcutSheet(
        show = showShortcutDialog.value,
        shortcutState = shortcutState,
        onDismiss = { showShortcutDialog.value = false },
        onPickShortcutIcon = { pickShortcutIconLauncher.launch("image/*") },
        onDeleteShortcut = {
            shortcutState.deleteShortcut(context)
            showShortcutDialog.value = false
        },
        onConfirmShortcut = {
            shortcutState.createShortcut(context)
            showShortcutDialog.value = false
        },
    )
}

@Composable
private fun ModuleList(
    bottomInnerPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    displayModules: List<Module>,
    recommendedModules: List<RecommendedModule> = emptyList(),
    pendingItems: List<ModulePendingItem> = emptyList(),
    selectedPendingIds: Set<String> = emptySet(),
    isBatchProcessing: Boolean = false,
    showPendingCard: Boolean = false,
    updateInfoMap: Map<String, ModuleUpdateInfo>,
    actions: ModuleActions,
    installRecommendedEnabled: Boolean = false,
    mutationsBlockedReason: String? = null,
    showEmptyPlaceholder: Boolean = false,
    onClickModule: (Module) -> Unit,
    onModuleAddShortcut: (Module, ShortcutType) -> Unit,
    closeSearch: () -> Unit? = {},
) {
    val loadingDialog = rememberLoadingDialog()
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp + bottomInnerPadding + 56.dp + 16.dp
        ),
    ) {
        if (mutationsBlockedReason != null) {
            item(key = "interface_blocked", contentType = "banner") {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.module_interface_blocked_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(mutationsBlockedReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        if (showPendingCard && pendingItems.isNotEmpty()) {
            item(key = "pending_todo", contentType = "pending_todo") {
                Column {
                    ExpressiveSectionTitle(title = stringResource(R.string.module_pending_section))
                    PendingTodoCard(
                        items = pendingItems,
                        selectedIds = selectedPendingIds,
                        isBatchProcessing = isBatchProcessing,
                        installEnabled = installRecommendedEnabled,
                        onToggle = actions.onTogglePendingSelection,
                        onProcessSelected = actions.onProcessSelectedPending,
                    )
                }
            }
        }
        if (recommendedModules.isNotEmpty()) {
            item(key = "recommended_header", contentType = "section_header") {
                ExpressiveSectionTitle(title = stringResource(R.string.module_recommended))
            }
            items(recommendedModules, key = { "rec_${it.id}" }, contentType = { "recommended" }) { module ->
                RecommendedModuleItem(
                    module = module,
                    installEnabled = installRecommendedEnabled,
                    onOpenHomepage = { actions.onOpenRecommendedHomepage(module) },
                    onInstall = { actions.onInstallRecommended(module) },
                )
            }
            if (displayModules.isNotEmpty()) {
                item(key = "installed_header", contentType = "section_header") {
                    ExpressiveSectionTitle(
                        title = stringResource(R.string.module_installed_section),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else if (showEmptyPlaceholder && displayModules.isEmpty() && pendingItems.isEmpty()) {
            item(key = "module_empty", contentType = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.module_empty),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        items(displayModules, key = { it.id }, contentType = { "module" }) { module ->
            val scope = rememberCoroutineScope()
            val moduleUpdateInfo = updateInfoMap[module.id] ?: ModuleUpdateInfo.Empty

            ModuleItem(
                module = module,
                updateUrl = if (mutationsBlockedReason == null) moduleUpdateInfo.downloadUrl else "",
                onUninstallClicked = {
                    if (module.remove) {
                        actions.onUndoUninstallModule(module)
                    } else {
                        actions.onRequestUninstallConfirmation(module)
                    }
                },
                onCheckChanged = {
                    actions.onToggleModule(module)
                },
                onUpdate = {
                    scope.launch {
                        loadingDialog.withLoading {
                            actions.onRequestUpdateConfirmation(module, moduleUpdateInfo)
                        }
                    }
                },
                onAddShortcut = { type -> onModuleAddShortcut(module, type) },
                onClick = { onClickModule(module) },
                onExecuteAction = { actions.onExecuteModuleAction(module) },
                onOpenDetails = { actions.onOpenModuleDetail(module); closeSearch() },
                closeSearch = { closeSearch() }
            )
        }
    }
}

@Composable
private fun ModuleShortcutSheet(
    show: Boolean,
    shortcutState: ModuleShortcutState,
    onDismiss: () -> Unit,
    onPickShortcutIcon: () -> Unit,
    onDeleteShortcut: () -> Unit,
    onConfirmShortcut: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val resources = LocalResources.current

    fun copyShortcutUrl() {
        val url = shortcutState.buildShortcutUrl() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KernelSU deep link", url))
        Toast.makeText(context, resources.getString(R.string.module_shortcut_scheme_copied), Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.module_shortcut_title),
                style = MaterialTheme.typography.titleLarge
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 13.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(25.dp))
            ) {
                val preview = shortcutState.previewIcon
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        modifier = Modifier.size(100.dp),
                        contentDescription = null,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.White)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = FixedScale(1.5f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPickShortcutIcon,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.module_shortcut_icon_pick)
                    )
                }
                AnimatedVisibility(
                    visible = shortcutState.iconUri != shortcutState.defaultShortcutIconUri,
                    enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                    exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    IconButton(
                        onClick = shortcutState::resetIconToDefault,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = shortcutState.name,
                onValueChange = shortcutState::updateName,
                label = { Text(stringResource(id = R.string.module_shortcut_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp)
            )
            if (shortcutState.hasExistingShortcut) {
                TextButton(
                    onClick = onDeleteShortcut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                ) {
                    Text(stringResource(id = R.string.module_shortcut_delete))
                }
            }
            TextButton(
                onClick = ::copyShortcutUrl,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            ) {
                Text(stringResource(id = R.string.module_shortcut_copy_scheme))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = android.R.string.cancel))
                }
                Button(
                    onClick = onConfirmShortcut,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (shortcutState.hasExistingShortcut) {
                            stringResource(id = R.string.module_update)
                        } else {
                            stringResource(id = android.R.string.ok)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingTodoCard(
    items: List<ModulePendingItem>,
    selectedIds: Set<String>,
    isBatchProcessing: Boolean,
    installEnabled: Boolean,
    onToggle: (String) -> Unit,
    onProcessSelected: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val selectable = items.filter { it.selectable }
    val uninstalls = items.filter { it.kind == ModulePendingKind.Uninstall }
    val selectedCount = selectedIds.size
    val atLimit = selectedCount >= MAX_MODULE_PENDING_SELECTION
    val busy = isBatchProcessing || !installEnabled

    val rows: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            selectable.forEach { item ->
                val selected = item.id in selectedIds
                val enabled = !isBatchProcessing && installEnabled && (selected || !atLimit)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = selected,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onToggle(item.id)
                            },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                if (item.kind == ModulePendingKind.Update) {
                                    R.string.module_pending_update
                                } else {
                                    R.string.module_pending_install
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            uninstalls.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.module_pending_uninstall),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (selectable.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ExpressivePrimaryBar(
                    label = stringResource(R.string.module_pending_process),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onProcessSelected()
                    },
                    enabled = !busy && selectedCount > 0,
                    icon = Icons.Outlined.Download,
                )
            }
        }
    }

    if (selectable.isNotEmpty()) {
        ExpressiveHeroCard(
            title = stringResource(R.string.module_pending_title),
            summary = buildString {
                append(stringResource(R.string.module_pending_summary_count, items.size))
                append('\n')
                append(stringResource(R.string.module_pending_max, MAX_MODULE_PENDING_SELECTION))
            },
            icon = if (isBatchProcessing) null else Icons.Outlined.SystemUpdateAlt,
            iconContent = if (isBatchProcessing) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                    )
                }
            } else {
                null
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            footer = { rows() },
        )
    } else {
        ExpressiveNoticeCard(
            message = stringResource(R.string.module_pending_uninstall_hint),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            icon = Icons.Outlined.Info,
            action = { rows() },
        )
    }
}

@Composable
private fun RecommendedModuleItem(
    module: RecommendedModule,
    installEnabled: Boolean,
    onOpenHomepage: () -> Unit,
    onInstall: () -> Unit,
) {
    val noteText = if (!module.hasDownload) {
        stringResource(R.string.module_recommended_no_download)
    } else {
        module.note
    }

    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 12.dp)
        ) {
            Text(
                text = module.name,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            )
            if (module.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = module.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (noteText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = noteText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = Dp.Hairline)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (module.homepage.isNotBlank()) {
                    OutlinedButton(
                        onClick = onOpenHomepage,
                        modifier = Modifier.defaultMinSize(56.dp, 48.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(
                            text = stringResource(R.string.module_recommended_homepage),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onInstall,
                    enabled = installEnabled && module.hasDownload,
                    modifier = Modifier.defaultMinSize(56.dp, 48.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 7.dp),
                        text = stringResource(R.string.module_install),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleItem(
    module: Module, updateUrl: String, onUninstallClicked: () -> Unit,
    onCheckChanged: (Boolean) -> Unit, onUpdate: () -> Unit,
    onAddShortcut: (ShortcutType) -> Unit, onClick: () -> Unit,
    onExecuteAction: () -> Unit, onOpenDetails: () -> Unit, closeSearch: () -> Unit,
) {
    var expanded by remember(module.id) { mutableStateOf(false) }
    var overflow by remember(module.id, module.description) { mutableStateOf(false) }
    var more by remember(module.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val enabled = module.enabled && !module.remove
    val scope = rememberCoroutineScope()
    val sheet = androidx.compose.material3.rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val pending = module.remove || module.update
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (enabled) colors.primaryContainer else colors.surfaceContainerLow,
            contentColor = if (enabled) colors.onPrimaryContainer else colors.onSurface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.large, color = if (enabled) colors.surface.copy(alpha = .65f) else colors.surfaceContainerHigh) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        me.weishu.kernelsu.ui.component.module.InstalledModuleArtwork(module.id, module.versionCode, module.webUiIconPath ?: module.actionIconPath, Modifier.size(32.dp))
                    }
                }
                Column(Modifier.weight(1f).clickable(onClick = onOpenDetails)) {
                    Text(module.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(module.version, module.author).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { more = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.module_card_more))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val status = when {
                    module.remove -> stringResource(R.string.module_pending_uninstall)
                    module.update -> stringResource(R.string.module_sheet_restart_required)
                    enabled -> stringResource(R.string.module_card_enabled)
                    else -> stringResource(R.string.module_card_disabled)
                }
                Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Switch(checked = module.enabled, enabled = !module.remove,
                    onCheckedChange = onCheckChanged,
                    modifier = Modifier.semantics { contentDescription = module.name; stateDescription = status })
            }
            if (updateUrl.isNotBlank() && !module.remove) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(16.dp))
                    Text(stringResource(R.string.module_card_update_available), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (module.metamodule) Text("META", style = MaterialTheme.typography.labelMedium)
            module.preinitWarning()?.let { Text(stringResource(it), color = colors.error, style = MaterialTheme.typography.labelMedium) }
            if (module.description.isNotBlank()) {
                Text(module.description, style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else if (enabled) 2 else 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
                if (overflow || expanded) TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.module_card_collapse else R.string.module_card_expand))
                }
            }
            if (enabled && !pending && (module.hasWebUi || module.hasActionScript)) {
                Button(onClick = { if (module.hasWebUi) onClick() else onExecuteAction(); closeSearch() },
                    shapes = ButtonDefaults.shapes()) {
                    Icon(if (module.hasWebUi) Icons.Outlined.Code else Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (module.hasWebUi) stringResource(R.string.module_card_open_webui) else stringResource(R.string.action))
                }
            }
        }
    }
    if (more) ModalBottomSheet(sheetState = sheet, onDismissRequest = { more = false }) {
        fun choose(action: () -> Unit) { scope.launch { sheet.hide(); more = false; action() } }
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(module.name, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
            @Composable fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, error: Boolean = false, action: () -> Unit) {
                androidx.compose.material3.ListItem(
                    content = { Text(label, color = if (error) colors.error else colors.onSurface) },
                    leadingContent = { Icon(icon, null, tint = if (error) colors.error else colors.onSurfaceVariant) },
                    modifier = Modifier.clickable { choose(action) })
            }
            item(stringResource(R.string.module_card_details), Icons.Outlined.Info, action = onOpenDetails)
            if (!module.remove) {
                if (updateUrl.isNotBlank()) item(stringResource(R.string.module_update), Icons.Outlined.Download, action = onUpdate)
                if (enabled && !pending) {
                    if (module.hasWebUi && module.hasActionScript) item(stringResource(R.string.action), Icons.Outlined.PlayArrow, action = onExecuteAction)
                    if (module.hasWebUi) item(stringResource(R.string.module_sheet_web_shortcut), Icons.Outlined.Code) { onAddShortcut(ShortcutType.WebUI) }
                    if (module.hasActionScript) item(stringResource(R.string.module_sheet_action_shortcut), Icons.Outlined.PlayArrow) { onAddShortcut(ShortcutType.Action) }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            item(stringResource(if (module.remove) R.string.undo else R.string.uninstall), Icons.Outlined.Delete, !module.remove,
                action = onUninstallClicked)
        }
    }
}
