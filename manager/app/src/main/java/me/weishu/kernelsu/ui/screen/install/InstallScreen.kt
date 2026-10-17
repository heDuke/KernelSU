package me.weishu.kernelsu.ui.screen.install

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.screen.home.DashboardInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.screen.home.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.message
import me.weishu.kernelsu.ui.component.choosekmidialog.ChooseKmiDialog
import me.weishu.kernelsu.ui.component.dialog.DownloadDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.LkmSelection
import me.weishu.kernelsu.ui.util.getAvailablePartitions
import me.weishu.kernelsu.ui.util.getCurrentKmi
import me.weishu.kernelsu.ui.util.getDefaultPartition
import me.weishu.kernelsu.ui.util.getSlotSuffix
import me.weishu.kernelsu.ui.util.isAbDevice
import me.weishu.kernelsu.ui.util.probeRemoteBootPartitions
import me.weishu.kernelsu.ui.util.rootAvailable

@Composable
fun InstallScreen(preselectedLkm: Uri? = null, preferOta: Boolean = false, checkOnlyMode: Boolean = false) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var probeJob by remember { mutableStateOf<Job?>(null) }
    val loadingDialog = rememberLoadingDialog()

    var installMethod by rememberSaveable { mutableStateOf<InstallMethod?>(null) }
    var downloadDialogShown by rememberSaveable { mutableStateOf(false) }
    var remotePartitions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var remotePartitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var lkmSelection by rememberSaveable { mutableStateOf<LkmSelection>(preselectedLkm?.let { LkmSelection.LkmUri(it) } ?: LkmSelection.KmiNone) }
    var partitionSelectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var hasCustomSelected by rememberSaveable { mutableStateOf(false) }
    val showChooseKmiDialog = rememberSaveable { mutableStateOf(false) }
    var advancedOptionsShown by rememberSaveable { mutableStateOf(false) }
    var blockModulesEnabled by rememberSaveable { mutableStateOf(false) }
    var blockModules by rememberSaveable { mutableStateOf("") }
    var allowShell by rememberSaveable { mutableStateOf(false) }
    var enableAdb by rememberSaveable { mutableStateOf(false) }
    var forceBackup by rememberSaveable { mutableStateOf(false) }
    var flashLocalImage by rememberSaveable { mutableStateOf(false) }
    var outputUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var review by rememberSaveable { mutableStateOf<InstallReviewSnapshot?>(null) }
    var preflightJob by remember { mutableStateOf<Job?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var reviewError by rememberSaveable { mutableStateOf<String?>(null) }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) outputUri = uri
    }
    val reportExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val report = review?.report
        if (uri != null && report != null) scope.launch {
            runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(report) }
            } }.exceptionOrNull()?.let { snackbarHost.showSnackbar(it.message.orEmpty()) }
        }
    }

    val currentKmi by produceState(initialValue = "") { value = getCurrentKmi() }
    val partitions by produceState(initialValue = emptyList()) { value = getAvailablePartitions() }
    val defaultPartition by produceState(initialValue = "") { value = getDefaultPartition() }
    val rootAvailable by produceState(initialValue = false) { value = rootAvailable() }
    val isAbDevice by produceState<Boolean?>(initialValue = null) { value = isAbDevice() }
    val selectFileTip = stringResource(id = R.string.select_file_tip, defaultPartition)
    val downloadFileMsg = stringResource(id = R.string.download_dialog_msg)
    val selectFileSummary = selectFileTip
    // Eligibility is checked against the selected image/module by the patcher.
    val canDirectInstall = rootAvailable
    LaunchedEffect(canDirectInstall, installMethod, isAbDevice, reviewing, review != null) {
        if (reviewing || review != null) return@LaunchedEffect
        if (preferOta && isAbDevice == null) return@LaunchedEffect
        when {
            installMethod is InstallMethod.DownloadFile -> {
                installMethod = if (canDirectInstall) InstallMethod.DirectInstall else null
                downloadDialogShown = false
            }
            installMethod == null && canDirectInstall -> {
                installMethod = if (preferOta && isAbDevice == true) InstallMethod.DirectInstallToInactiveSlot else InstallMethod.DirectInstall
            }
        }
    }
    val installMethodOptions = remember(
        rootAvailable,
        isAbDevice,
        selectFileSummary,
        advancedOptionsShown,
        installMethod,
    ) {
        buildList {
            if (canDirectInstall) {
                add(InstallMethod.DirectInstall)
                if (isAbDevice == true) add(InstallMethod.DirectInstallToInactiveSlot)
                if (installMethod is InstallMethod.SelectFile && !advancedOptionsShown) {
                    add(InstallMethod.SelectFile(summary = selectFileSummary))
                }
            } else {
                add(InstallMethod.SelectFile(summary = selectFileSummary))
            }
        }
    }

    val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
    val slotSuffix by produceState(initialValue = "", isOta) { value = getSlotSuffix(isOta) }
    val defaultIndex = remember(partitions, defaultPartition) {
        partitions.indexOf(defaultPartition).coerceAtLeast(0)
    }

    LaunchedEffect(partitions, defaultIndex, hasCustomSelected, reviewing, review != null) {
        if (reviewing || review != null) return@LaunchedEffect
        if (partitions.isEmpty()) return@LaunchedEffect
        if (!hasCustomSelected) {
            partitionSelectionIndex = defaultIndex.coerceIn(0, partitions.lastIndex)
        } else if (partitionSelectionIndex > partitions.lastIndex) {
            partitionSelectionIndex = partitions.lastIndex
        }
    }

    val displayPartitions = remember(partitions, defaultPartition) {
        partitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }
    val remoteDisplayPartitions = remember(remotePartitions, defaultPartition) {
        remotePartitions.map { name -> if (defaultPartition == name) "$name (default)" else name }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHost.showSnackbar(message)
        }
    }

    fun prepare(checkOnly: Boolean) {
        val readOnly = checkOnly || checkOnlyMode
        val method = installMethod ?: return
        if (reviewing) return
        if (blockModulesEnabled && runCatching { me.weishu.kernelsu.core.utils.parseBootModuleBlocklist(blockModules) }.isFailure) {
            reviewError = resources.getString(R.string.install_block_modules_invalid)
            return
        }
        reviewing = true; reviewError = null
        // Snapshot selections before doing IO; subsequent UI edits cannot change this operation.
        val selection = lkmSelection
        val partition = if (method is InstallMethod.DownloadFile) method.partition else partitions.getOrNull(partitionSelectionIndex)
        val chosenOutput = outputUri
        val chosenBlockModules = if (blockModulesEnabled) me.weishu.kernelsu.core.utils.parseBootModuleBlocklist(blockModules).joinToString(",") else ""
        val chosenShell = allowShell; val chosenAdb = enableAdb; val chosenBackup = forceBackup
        val chosenOta = method is InstallMethod.DirectInstallToInactiveSlot
        val chosenLocalFlash = flashLocalImage && method is InstallMethod.SelectFile
        preflightJob = scope.launch {
            val inputs = linkedMapOf<String, String>()
            try {
                if (method is InstallMethod.SelectFile) requireNotNull(method.uri) { resources.getString(R.string.dashboard_select_input) }
                val image = (method as? InstallMethod.SelectFile)?.uri?.let { stageBootInput(context, it, false) }
                image?.let { inputs[it.uri.toString()] = it.sha256 }
                val module = (selection as? LkmSelection.LkmUri)?.uri?.let { stageBootInput(context, it, true) }
                module?.let { inputs[it.uri.toString()] = it.sha256 }
                val frozenSelection = module?.let { LkmSelection.LkmUri(it.uri) } ?: selection
                val pageResult = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { me.weishu.kernelsu.ui.util.getKernelPageSize() } }
                pageResult.exceptionOrNull()?.let { android.util.Log.w("KernelSU", "Kernel page size unavailable", it) }
                val page = pageResult.getOrNull()
                val pageError = pageResult.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
                val release = android.system.Os.uname().release
                val chosenSlot = withContext(kotlinx.coroutines.Dispatchers.IO) { getSlotSuffix(chosenOta) }
                if (isAbDevice == true) require(chosenSlot in setOf("_a", "_b")) { "Cannot determine the target slot" }
                val metadata = module?.inspection
                val direct = method is InstallMethod.DirectInstall || method is InstallMethod.DirectInstallToInactiveSlot || chosenLocalFlash
                val targetRelease = if (chosenLocalFlash) image?.inspection?.release else release
                val targetPage = if (chosenLocalFlash) image?.inspection?.kernelPageSize else page
                val targetKmi = if (chosenLocalFlash) image?.inspection?.kmi else currentKmi
                val conflict = (direct && (partition == null || partition !in partitions)) ||
                    (chosenLocalFlash && (targetRelease == null || targetPage == null || image?.inspection?.partition != partition || !android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a"))) ||
                    (metadata != null && direct && ((metadata.release != null && targetRelease != null && metadata.release != targetRelease) ||
                    (metadata.kernelPageSize != null && targetPage != null && metadata.kernelPageSize != targetPage) ||
                    (metadata.kmi != null && !targetKmi.isNullOrBlank() && metadata.kmi != targetKmi)))
                val operation = if (method is InstallMethod.DownloadFile) FlashIt.DownloadBoot(
                    url = requireNotNull(method.url), partition = requireNotNull(method.partition), lkm = frozenSelection,
                    allowShell = chosenShell, enableAdb = chosenAdb, backup = chosenBackup, blockModules = chosenBlockModules)
                else FlashIt.FlashBoot(boot = image?.uri, lkm = frozenSelection,
                    ota = method is InstallMethod.DirectInstallToInactiveSlot, partition = partition,
                    allowShell = chosenShell, enableAdb = chosenAdb, backup = method is InstallMethod.SelectFile && chosenBackup,
                    outputUri = if (image != null) chosenOutput else null, flashSourceImage = chosenLocalFlash, blockModules = chosenBlockModules)
                val unknown = resources.getString(R.string.dashboard_unknown)
                val gateFacts = withContext(kotlinx.coroutines.Dispatchers.IO) { me.weishu.kernelsu.ui.util.currentRootGateFacts() }
                val kernelUapiText = gateFacts.kernelUapi?.toString() ?: unknown
                val managerUapiText = gateFacts.managerUapi.toString()
                val gateReason = if (direct) me.weishu.kernelsu.core.utils.evaluateRootGate(me.weishu.kernelsu.core.utils.RootOperation.BootPatch, gateFacts).message(context) else null
                val pageText = page?.toString() ?: pageError?.let { resources.getString(R.string.dashboard_page_size_failed, it) } ?: unknown
                val report = buildString {
                    appendLine(resources.getString(R.string.dashboard_review))
                    appendLine(resources.getString(method.label))
                    if (chosenBlockModules.isNotBlank()) appendLine("${resources.getString(R.string.install_block_modules_list)}: $chosenBlockModules")
                    appendLine("${resources.getString(R.string.dashboard_device)}: ${android.os.Build.MODEL}")
                    appendLine("${resources.getString(R.string.dashboard_build)}: ${android.os.Build.ID}")
                    appendLine("${resources.getString(R.string.dashboard_kernel)}: $release")
                    appendLine("KMI: $currentKmi")
                    appendLine(resources.getString(R.string.dashboard_uapi_versions, kernelUapiText, managerUapiText))
                    gateReason?.let { appendLine(it) }
                    appendLine("${resources.getString(R.string.dashboard_page_size)}: $pageText")
                    appendLine("${resources.getString(R.string.dashboard_restore_target)}: ${partition ?: unknown} $chosenSlot")
                    appendLine("${resources.getString(R.string.dashboard_output)}: " + resources.getString(if (chosenLocalFlash) R.string.dashboard_patch_flash_output else if (direct) R.string.dashboard_direct_output else R.string.dashboard_local_output))
                    if (image != null) appendLine("output_uri=${chosenOutput ?: resources.getString(R.string.dashboard_default_output)}")
                    for (input in listOfNotNull(image, module)) {
                        appendLine(); appendLine("${resources.getString(R.string.dashboard_file)}: ${input.name}")
                        appendLine("format=${input.inspection.format} bytes=${input.bytes}")
                        appendLine("SHA-256=${input.sha256}")
                        appendLine("declared_release=${input.inspection.release ?: unknown}")
                        appendLine("declared_kmi=${input.inspection.kmi ?: unknown}; image_partition=${input.inspection.partition ?: unknown}")
                        appendLine("declared_kernel_page_size=${input.inspection.kernelPageSize ?: unknown}")
                        appendLine("image_alignment=${input.inspection.imagePageSize ?: unknown}")
                        appendLine("signature_present=${input.inspection.signaturePresent ?: unknown}; signature_trust=$unknown; CFI/CRC=$unknown")
                    }
                    if (method is InstallMethod.DownloadFile) appendLine("remote_url=${method.url}; remote_digest=$unknown")
                    appendLine("allow_shell=$chosenShell enable_adbd=$chosenAdb force_stock_backup=$chosenBackup")
                    appendLine(resources.getString(R.string.dashboard_experimental_gate))
                    appendLine(resources.getString(R.string.dashboard_preflight_note))
                    if (conflict) appendLine(resources.getString(R.string.dashboard_check_failure))
                    appendLine("load_attempted=false; partitions_written=false")
                }
                val outputName = chosenOutput?.let { destination -> withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.query(destination, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0).filterNot(Char::isISOControl).take(160) else null
                    } }.getOrNull() ?: unknown
                } }
                val summary = linkedMapOf(
                    resources.getString(R.string.home_sheet_operation) to resources.getString(method.label),
                    resources.getString(R.string.dashboard_device) to android.os.Build.MODEL,
                    resources.getString(R.string.dashboard_interface) to resources.getString(R.string.dashboard_uapi_versions, kernelUapiText, managerUapiText),
                    resources.getString(R.string.dashboard_restore_target) to "${partition ?: unknown} ${chosenSlot.ifBlank { unknown }}",
                    resources.getString(R.string.dashboard_output) to resources.getString(if (readOnly) R.string.dashboard_check_only else if (chosenLocalFlash) R.string.dashboard_patch_flash_output else if (direct) R.string.dashboard_direct_output else R.string.dashboard_local_output),
                    resources.getString(R.string.home_sheet_backup_policy) to resources.getString(if (chosenBackup) R.string.home_sheet_backup_required else R.string.home_sheet_backup_backend))
                if (outputName != null) summary[resources.getString(R.string.dashboard_output)] = outputName
                gateReason?.let { summary[resources.getString(R.string.dashboard_blocked_reason)] = it }
                listOfNotNull(image, module).forEachIndexed { index, input -> summary[resources.getString(R.string.dashboard_file) + " ${index + 1}"] = input.name + " · " + android.text.format.Formatter.formatFileSize(context, input.bytes) }
                review = InstallReviewSnapshot(operation, report, inputs, !conflict && gateReason == null, readOnly,
                    expectedSlot = chosenSlot, expectedRelease = release, expectedFingerprint = android.os.Build.FINGERPRINT, summary = summary)
            } catch (error: Exception) {
                releaseReviewedInputs(context, inputs)
                if (error is CancellationException) throw error
                reviewError = error.message
                snackbarHost.showSnackbar(error.message ?: resources.getString(R.string.dashboard_report_error))
            } finally { reviewing = false }
        }
    }
    val onInstall = { prepare(false) }

    ChooseKmiDialog(
        show = showChooseKmiDialog.value,
        onDismissRequest = { showChooseKmiDialog.value = false },
        onSelected = { kmi ->
            kmi?.let {
                lkmSelection = LkmSelection.KmiString(it)
                onInstall()
            }
        }
    )

    DownloadDialog(
        show = downloadDialogShown,
        onConfirm = { url ->
            downloadDialogShown = false
            probeJob?.cancel()
            probeJob = scope.launch {
                try {
                    loadingDialog.showLoading()
                    val result = probeRemoteBootPartitions(url)
                    if (result.partitions.isEmpty()) {
                        showMessage(resources.getString(R.string.download_no_boot_partition))
                    } else {
                        val defaultIdx = result.partitions.indexOf(defaultPartition).coerceAtLeast(0)
                        remotePartitions = result.partitions
                        remotePartitionSelectionIndex = defaultIdx
                        installMethod = InstallMethod.DownloadFile(
                            url = url,
                            partition = result.partitions[defaultIdx],
                            summary = downloadFileMsg,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(
                        resources.getString(R.string.download_probe_failed, e.message ?: "")
                    )
                } finally {
                    loadingDialog.hide()
                }
            }
        },
        onDismiss = { downloadDialogShown = false }
    )

    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                if (isKoFile(context, uri)) {
                    lkmSelection = LkmSelection.LkmUri(uri)
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    showMessage(resources.getString(R.string.install_only_support_ko_file))
                }
            }
        }
    }
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                installMethod = InstallMethod.SelectFile(uri, summary = selectFileSummary)
            }
        }
    }

    val state = InstallUiState(
        installMethod = installMethod,
        lkmSelection = lkmSelection,
        partitionSelectionIndex = partitionSelectionIndex,
        displayPartitions = displayPartitions,
        remoteDisplayPartitions = remoteDisplayPartitions,
        remotePartitionSelectionIndex = remotePartitionSelectionIndex,
        currentKmi = currentKmi,
        slotSuffix = slotSuffix,
        installMethodOptions = installMethodOptions,
        canSelectPartition = installMethod is InstallMethod.DirectInstall ||
            installMethod is InstallMethod.DirectInstallToInactiveSlot ||
            installMethod is InstallMethod.DownloadFile || (installMethod is InstallMethod.SelectFile && flashLocalImage),
        advancedOptionsShown = advancedOptionsShown,
        allowShell = allowShell,
        blockModulesEnabled = blockModulesEnabled, blockModules = blockModules,
        enableAdb = enableAdb,
        forceBackup = forceBackup,
        canForceBackup = installMethod is InstallMethod.SelectFile,
        showAdvancedSelectFile = canDirectInstall,
        selectFileSummary = selectFileSummary,
        flashLocalImage = flashLocalImage,
        canFlashLocalImage = rootAvailable && !checkOnlyMode,
        checkOnly = checkOnlyMode,
        reviewing = reviewing,
        outputSummary = outputUri?.toString(),
    )
    val actions = InstallScreenActions(
        onCheckOnly = { prepare(true) },
        onSelectLocalFlash = { flashLocalImage = it },
        onPickOutput = { outputPicker.launch("HuskySU-patched-${System.currentTimeMillis()}.img") },
        onClearOutput = { outputUri = null },
        onBack = dropUnlessResumed { navigator.pop() },
        onSelectMethod = { method -> installMethod = method },
        onDownloadFile = { downloadDialogShown = true },
        onSelectBootImage = {
            selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/octet-stream" })
        },
        onUploadLkm = {
            selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/octet-stream" })
        },
        onClearLkm = { lkmSelection = LkmSelection.KmiNone },
        onSelectPartition = { index ->
            hasCustomSelected = true
            val method = installMethod
            if (method is InstallMethod.DownloadFile) {
                remotePartitionSelectionIndex = index
                installMethod = method.copy(partition = remotePartitions.getOrNull(index))
            } else {
                partitionSelectionIndex = index
            }
        },
        onNext = {
            val isLkmSelected = lkmSelection != LkmSelection.KmiNone
            val isKmiUnknown = currentKmi.isBlank()
            val isKmiUnresolved = when (installMethod) {
                // The download flow extracts the KMI itself; no manual
                // selection needed.
                is InstallMethod.DownloadFile -> false
                is InstallMethod.SelectFile -> true
                else -> isKmiUnknown
            }
            if (!isLkmSelected && isKmiUnresolved) {
                showChooseKmiDialog.value = true
            } else {
                onInstall()
            }
        },
        onAdvancedOptionsClicked = {
            advancedOptionsShown = !advancedOptionsShown
        },
        onSelectBlockModules = { blockModulesEnabled = it },
        onBlockModulesChanged = { if (it.length <= 4096) blockModules = it },
        onSelectAllowShell = {
            allowShell = it
        },
        onSelectEnableAdb = {
            enableAdb = it
        },
        onSelectForceBackup = {
            forceBackup = it
        }
    )

    InstallScreenMaterial(state, actions, snackbarHost)
    if (reviewing) AlertDialog(onDismissRequest = { preflightJob?.cancel() },
        title = { Text(stringResource(R.string.home_sheet_preflight_working)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { LoadingIndicator(); Text(stringResource(R.string.home_sheet_preflight_note)) } },
        confirmButton = {}, dismissButton = { TextButton(onClick = { preflightJob?.cancel() }) { Text(stringResource(android.R.string.cancel)) } })
    review?.let { snapshot ->
        fun dismissReview() {
            if (!checking) { review = null; scope.launch { releaseReviewedInputs(context, snapshot.inputs) } }
        }
        HomeInformationSheet(snapshot.inputs, stringResource(if (snapshot.checkOnly) R.string.dashboard_check_only else R.string.dashboard_review),
            onDismiss = ::dismissReview, dismissEnabled = !checking,
            onReport = { reportExport.launch("HuskySU-preflight-${System.currentTimeMillis()}.txt") },
            footer = if (snapshot.checkOnly) null else ({
                if (checking) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.home_sheet_preflight_verifying)) }
                Button(enabled = snapshot.allowed && !checking, shapes = ButtonDefaults.shapes(), onClick = {
                    checking = true
                    scope.launch {
                        try {
                            verifyReviewedInputs(context, snapshot.inputs)
                            if ((snapshot.operation as? FlashIt.FlashBoot)?.let { it.boot == null || it.flashSourceImage } == true) {
                                val op = snapshot.operation
                                check(android.os.Build.FINGERPRINT == snapshot.expectedFingerprint && android.system.Os.uname().release == snapshot.expectedRelease) { "Device/build changed; review again" }
                                check(withContext(kotlinx.coroutines.Dispatchers.IO) { getSlotSuffix(op.ota) } == snapshot.expectedSlot) { "Target slot changed; review again" }
                                val gate = withContext(kotlinx.coroutines.Dispatchers.IO) { me.weishu.kernelsu.ui.util.currentRootGate(me.weishu.kernelsu.core.utils.RootOperation.BootPatch) }
                                gate.message(context)?.let { reason -> error(reason) }
                            }
                            review = null
                            navigator.push(Route.Flash(snapshot.operation))
                        } catch (error: Exception) { if (error is CancellationException) throw error; reviewError = error.message }
                        finally { checking = false }
                    }

                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_confirm_write)) }
            })) {
            HomeStateCard(stringResource(if (!snapshot.allowed) R.string.dashboard_check_failure else if (snapshot.checkOnly) R.string.home_sheet_check_completed else R.string.home_sheet_ready_review),
                stringResource(if (snapshot.checkOnly) R.string.home_sheet_check_only_note else R.string.dashboard_install_warning), Icons.AutoMirrored.Outlined.FactCheck,
                if (!snapshot.allowed) HomeTone.Error else HomeTone.Primary)
            HomeFacts(stringResource(R.string.home_sheet_execution_summary), snapshot.summary.toList())
            Text(stringResource(R.string.dashboard_preflight_note))
            reviewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            HomeTechnicalDetails(snapshot.inputs) { androidx.compose.foundation.text.selection.SelectionContainer { Text(snapshot.report) } }
        }
    }
}
