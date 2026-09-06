package me.weishu.kernelsu.ui.util

import android.net.Uri
import me.weishu.kernelsu.Natives

/**
 * Loader-facing ksud / root operations (boot, LKM, slot switch, profiles, features).
 *
 * Package Manager UI should depend on [PackageClient] instead of calling these.
 * See docs/superpowers/specs/2026-09-06-loader-pm-split.md.
 */
object LoaderClient {
    fun installUserspace() = install()

    fun flashBoot(
        boot: Uri?,
        lkm: LkmSelection,
        ota: Boolean,
        partition: String?,
        allowShell: Boolean,
        enableAdb: Boolean,
        forceBackup: Boolean,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): FlashResult = installBoot(
        boot,
        lkm,
        ota,
        partition,
        allowShell,
        enableAdb,
        forceBackup,
        onStdout,
        onStderr,
    )

    fun flashRestore(
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): FlashResult = restoreBoot(onStdout, onStderr)

    fun flashUninstall(
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): FlashResult = uninstallPermanently(onStdout, onStderr)

    suspend fun currentKmi(): String = getCurrentKmi()

    suspend fun supportedKmis(): List<String> = getSupportedKmis()

    suspend fun abDevice(): Boolean = isAbDevice()

    suspend fun defaultPartition(): String = getDefaultPartition()

    suspend fun slotSuffix(ota: Boolean): String = getSlotSuffix(ota)

    suspend fun availablePartitions(): List<String> = getAvailablePartitions()

    fun superuserCount(): Int = Natives.getSuperuserCount()

    fun isRootAvailable(): Boolean = rootAvailable()

    suspend fun featureStatus(feature: String): String = getFeatureStatus(feature)

    suspend fun featurePersistValue(feature: String): Long? = getFeaturePersistValue(feature)
}

/**
 * Package-facing module operations.
 *
 * Loader screens should not call these for new code paths.
 * See docs/superpowers/specs/2026-09-06-loader-pm-split.md.
 */
object PackageClient {
    fun modulesJson(): String = listModules()

    fun moduleCount(): Int = getModuleCount()

    fun setModuleEnabled(id: String, enable: Boolean): Boolean = toggleModule(id, enable)

    fun removeModule(id: String): Boolean = uninstallModule(id)

    fun restoreModule(id: String): Boolean = undoUninstallModule(id)

    fun flashZip(
        uri: Uri,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): FlashResult = flashModule(uri, onStdout, onStderr)

    fun executeAction(
        moduleId: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): Boolean = runModuleAction(moduleId, onStdout, onStderr)
}
