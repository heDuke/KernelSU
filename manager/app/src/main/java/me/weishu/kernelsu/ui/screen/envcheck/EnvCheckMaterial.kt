package me.weishu.kernelsu.ui.screen.envcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.EnvCheckGroup
import me.weishu.kernelsu.data.model.EnvCheckItem
import me.weishu.kernelsu.data.model.EnvCheckReport
import me.weishu.kernelsu.data.model.EnvCheckSeverity
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressiveNoticeCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveSectionTitle
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvCheckScreenMaterial(
    isLoading: Boolean,
    report: EnvCheckReport?,
    error: String?,
    actions: EnvCheckScreenActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.env_check_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            isLoading && report == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && report == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    ExpressiveNoticeCard(
                        message = error,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )
                    ExpressivePrimaryBar(
                        label = stringResource(R.string.env_check_retry),
                        onClick = actions.onRefresh,
                        icon = Icons.Outlined.Refresh,
                    )
                }
            }

            report != null -> {
                val overallColor = when (report.overall) {
                    EnvCheckSeverity.Pass -> MaterialTheme.colorScheme.secondaryContainer
                    EnvCheckSeverity.Warn -> MaterialTheme.colorScheme.tertiaryContainer
                    EnvCheckSeverity.Fail -> MaterialTheme.colorScheme.errorContainer
                    EnvCheckSeverity.Unknown -> MaterialTheme.colorScheme.surfaceBright
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    item {
                        ExpressiveHeroCard(
                            title = overallTitle(report.overall),
                            summary = report.worstItem?.let { "${it.title}: ${it.detail}" }
                                ?: stringResource(R.string.env_check_all_good),
                            icon = Icons.Outlined.HealthAndSafety,
                            containerColor = overallColor,
                        )
                    }
                    item {
                        ExpressivePrimaryBar(
                            label = stringResource(R.string.env_check_retry),
                            onClick = actions.onRefresh,
                            enabled = !isLoading,
                            tonal = true,
                            icon = Icons.Outlined.Refresh,
                        )
                    }
                    item {
                        ExpressivePrimaryBar(
                            label = stringResource(R.string.env_check_copy),
                            onClick = { actions.onCopyReport(report) },
                            tonal = true,
                            icon = Icons.Outlined.ContentCopy,
                        )
                    }
                    if (error != null) {
                        item {
                            ExpressiveNoticeCard(
                                message = error,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        }
                    }
                    EnvCheckGroup.entries.forEach { group ->
                        val groupItems = report.items.filter { it.group == group }
                        if (groupItems.isEmpty()) return@forEach
                        item {
                            ExpressiveSectionTitle(groupTitle(group))
                        }
                        items(groupItems, key = { it.id }) { checkItem ->
                            EnvCheckItemCard(checkItem)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EnvCheckItemCard(item: EnvCheckItem) {
    val color = when (item.severity) {
        EnvCheckSeverity.Pass -> MaterialTheme.colorScheme.secondaryContainer
        EnvCheckSeverity.Warn -> MaterialTheme.colorScheme.tertiaryContainer
        EnvCheckSeverity.Fail -> MaterialTheme.colorScheme.errorContainer
        EnvCheckSeverity.Unknown -> MaterialTheme.colorScheme.surfaceBright
    }
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = color,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${severityLabel(item.severity)} · ${item.title}",
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun overallTitle(severity: EnvCheckSeverity): String = when (severity) {
    EnvCheckSeverity.Pass -> stringResource(R.string.env_check_overall_pass)
    EnvCheckSeverity.Warn -> stringResource(R.string.env_check_overall_warn)
    EnvCheckSeverity.Fail -> stringResource(R.string.env_check_overall_fail)
    EnvCheckSeverity.Unknown -> stringResource(R.string.env_check_overall_unknown)
}

@Composable
private fun severityLabel(severity: EnvCheckSeverity): String = when (severity) {
    EnvCheckSeverity.Pass -> stringResource(R.string.env_check_pass)
    EnvCheckSeverity.Warn -> stringResource(R.string.env_check_warn)
    EnvCheckSeverity.Fail -> stringResource(R.string.env_check_fail)
    EnvCheckSeverity.Unknown -> stringResource(R.string.env_check_unknown)
}

@Composable
private fun groupTitle(group: EnvCheckGroup): String = when (group) {
    EnvCheckGroup.Root -> stringResource(R.string.env_check_group_root)
    EnvCheckGroup.Version -> stringResource(R.string.env_check_group_version)
    EnvCheckGroup.Boot -> stringResource(R.string.env_check_group_boot)
    EnvCheckGroup.IntegrityOnline -> stringResource(R.string.env_check_group_integrity)
    EnvCheckGroup.Modules -> stringResource(R.string.env_check_group_modules)
    EnvCheckGroup.System -> stringResource(R.string.env_check_group_system)
}
