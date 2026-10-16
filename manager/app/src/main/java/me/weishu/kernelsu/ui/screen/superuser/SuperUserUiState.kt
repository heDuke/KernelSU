package me.weishu.kernelsu.ui.screen.superuser

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.data.model.AuthorizationTarget
import me.weishu.kernelsu.data.model.SuperUserFilter
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.viewmodel.AppSortConfig

@Immutable
data class GroupedApps(
    val uid: Int,
    val apps: List<AppInfo>,
    val primary: AppInfo,
    val anyAllowSu: Boolean,
    val anyCustom: Boolean,
    val shouldUmount: Boolean,
    val ownerName: String? = null,
    val matchedPackageNames: Set<String> = emptySet(),
) {
    fun authorizationTarget() = AuthorizationTarget(uid, apps.map { it.authorizationIdentity() }.toSet(), anyAllowSu)
}

@Stable
data class SuperUserUiState(
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val groupedApps: List<GroupedApps> = emptyList(),
    val recentlyInstalledResults: List<GroupedApps> = emptyList(),
    val userIds: List<Int> = emptyList(),
    val searchStatus: SearchStatus = SearchStatus(""),
    val searchResults: List<GroupedApps> = emptyList(),
    val showSystemApps: Boolean = false,
    val showOnlyPrimaryUserApps: Boolean = false,
    val sortConfig: AppSortConfig = AppSortConfig(),
    val filter: SuperUserFilter = SuperUserFilter.Granted,
    val savingUid: Int? = null,
    val mutationError: Throwable? = null,
    val mutationMessage: Boolean? = null,
    val error: Throwable? = null,
) {
    val visibleGroups get() = groupedApps.filter {
        when (filter) {
            SuperUserFilter.All -> true
            SuperUserFilter.Granted -> it.anyAllowSu
            SuperUserFilter.Custom -> it.anyCustom
        }
    }
}

@Immutable
data class SuperUserActions(
    val onRefresh: () -> Unit,
    val onOpenSulog: () -> Unit,
    val onOpenHome: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onSearchStatusChange: (SearchStatus) -> Unit,
    val onClearSearch: () -> Unit,
    val onToggleShowSystemApps: () -> Unit,
    val onToggleShowOnlyPrimaryUserApps: () -> Unit,
    val onUpdateSortConfig: (AppSortConfig) -> Unit,
    val onFilterChange: (SuperUserFilter) -> Unit,
    val onSetAuthorization: (AuthorizationTarget, Boolean) -> Unit,
    val onMessageShown: () -> Unit,
    val onOpenProfile: (GroupedApps) -> Unit,
)
