package me.weishu.kernelsu.ui.screen.module

import android.net.Uri
import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.data.model.ModuleUpdateInfo
import me.weishu.kernelsu.data.model.RecommendedModule
import me.weishu.kernelsu.ui.component.SearchStatus

sealed interface ModuleConfirmRequest {
    data class Update(
        val module: Module,
        val downloadUrl: String,
        val fileName: String,
    ) : ModuleConfirmRequest

    data class Uninstall(
        val module: Module,
    ) : ModuleConfirmRequest
}

@Immutable
data class ModuleConfirmDialogState(
    val request: ModuleConfirmRequest,
    val title: String,
    val content: String? = null,
    val markdown: Boolean = false,
    val html: Boolean = false,
    val confirm: String? = null,
    val dismiss: String? = null,
)

sealed interface ModuleEffect {
    data class Toast(
        val message: String,
    ) : ModuleEffect

    data class SnackBar(
        val message: String,
    ) : ModuleEffect
}

enum class ModulePendingKind {
    Update,
    Uninstall,
    Recommended,
}

@Immutable
data class ModulePendingItem(
    val id: String,
    val name: String,
    val kind: ModulePendingKind,
    val downloadUrl: String = "",
    val fileName: String = "",
) {
    val selectable: Boolean
        get() = kind != ModulePendingKind.Uninstall && downloadUrl.isNotBlank()
}

const val MAX_MODULE_PENDING_SELECTION = 5

@Immutable
data class ModuleUiState(
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val modules: List<Module> = emptyList(),
    val moduleList: List<Module> = emptyList(),
    val updateInfo: Map<String, ModuleUpdateInfo> = emptyMap(),
    val searchStatus: SearchStatus = SearchStatus(""),
    val searchResults: List<Module> = emptyList(),
    val sortEnabledFirst: Boolean = false,
    val sortActionFirst: Boolean = false,
    val checkModuleUpdate: Boolean = true,
    val isSafeMode: Boolean = false,
    val magiskInstalled: Boolean = false,
    val confirmDialogState: ModuleConfirmDialogState? = null,
    val recommendedModules: List<RecommendedModule> = emptyList(),
    val selectedPendingIds: Set<String> = emptySet(),
    val isBatchProcessing: Boolean = false,
) {
    val installButtonVisible: Boolean
        get() = !(isSafeMode || magiskInstalled)

    val visibleRecommendedModules: List<RecommendedModule>
        get() {
            val installedIds = modules.asSequence()
                .filter { !it.remove }
                .mapTo(HashSet()) { it.id }
            return recommendedModules.filter { it.id !in installedIds }
        }

    val pendingItems: List<ModulePendingItem>
        get() = buildPendingItems(modules, updateInfo, visibleRecommendedModules)

    val selectedPendingItems: List<ModulePendingItem>
        get() {
            if (selectedPendingIds.isEmpty()) return emptyList()
            return pendingItems.filter { it.selectable && it.id in selectedPendingIds }
        }
}

internal fun buildPendingItems(
    modules: List<Module>,
    updateInfo: Map<String, ModuleUpdateInfo>,
    recommended: List<RecommendedModule>,
): List<ModulePendingItem> {
    val items = ArrayList<ModulePendingItem>()
    val seen = HashSet<String>()

    for (module in modules) {
        if (module.remove) continue
        val info = updateInfo[module.id] ?: continue
        if (info.downloadUrl.isBlank()) continue
        items += ModulePendingItem(
            id = module.id,
            name = module.name,
            kind = ModulePendingKind.Update,
            downloadUrl = info.downloadUrl,
            fileName = "${module.name}-${info.version}.zip",
        )
        seen += module.id
    }

    for (module in modules) {
        if (!module.remove) continue
        items += ModulePendingItem(
            id = module.id,
            name = module.name,
            kind = ModulePendingKind.Uninstall,
        )
        seen += module.id
    }

    for (module in recommended) {
        if (!module.hasDownload || module.id in seen) continue
        items += ModulePendingItem(
            id = module.id,
            name = module.name,
            kind = ModulePendingKind.Recommended,
            downloadUrl = module.downloadUrl,
            fileName = module.zipFileName,
        )
        seen += module.id
    }

    return items
}

fun ModuleUiState.reconcilePendingSelection(): ModuleUiState {
    val selectableIds = pendingItems.mapNotNull { item ->
        item.id.takeIf { item.selectable }
    }
    val selectableSet = selectableIds.toSet()
    val valid = selectedPendingIds.filter { it in selectableSet }.toSet()
    val next = if (valid.isEmpty()) {
        selectableIds.take(MAX_MODULE_PENDING_SELECTION).toSet()
    } else {
        valid
    }
    return if (next == selectedPendingIds) this else copy(selectedPendingIds = next)
}

@Immutable
data class ModuleActions(
    val onRefresh: () -> Unit,
    val onSearchStatusChange: (SearchStatus) -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onRequestUpdateConfirmation: (Module, ModuleUpdateInfo) -> Unit,
    val onRequestUninstallConfirmation: (Module) -> Unit,
    val onDismissConfirmRequest: () -> Unit,
    val onConfirmUpdate: (ModuleConfirmRequest.Update) -> Unit,
    val onToggleSortActionFirst: () -> Unit,
    val onToggleSortEnabledFirst: () -> Unit,
    val onOpenWebUi: (Module) -> Unit,
    val onToggleModule: (Module) -> Unit,
    val onUninstallModule: (Module) -> Unit,
    val onUndoUninstallModule: (Module) -> Unit,
    val onOpenFlash: (List<Uri>) -> Unit,
    val onExecuteModuleAction: (Module) -> Unit,
    val onOpenRecommendedHomepage: (RecommendedModule) -> Unit,
    val onInstallRecommended: (RecommendedModule) -> Unit,
    val onOpenModuleRepo: () -> Unit,
    val onTogglePendingSelection: (String) -> Unit,
    val onProcessSelectedPending: () -> Unit,
)
