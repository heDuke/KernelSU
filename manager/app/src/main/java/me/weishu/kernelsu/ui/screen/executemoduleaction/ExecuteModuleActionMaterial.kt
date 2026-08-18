package me.weishu.kernelsu.ui.screen.executemoduleaction

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.KeyEventBlocker
import me.weishu.kernelsu.ui.component.material.ExpressiveHeroCard
import me.weishu.kernelsu.ui.component.material.ExpressivePrimaryBar
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ExecuteModuleActionScreenMaterial(
    state: ExecuteModuleActionUiState,
    actions: ExecuteModuleActionScreenActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollState = rememberScrollState()
    val threshold = with(LocalDensity.current) { 100.dp.toPx() }
    val fabExpanded by remember {
        var previousScroll = 0
        var scrollDelta = 0f
        var expanded = true
        derivedStateOf {
            val currentScroll = scrollState.value
            val delta = (currentScroll - previousScroll).toFloat()
            scrollDelta = (scrollDelta + delta).coerceIn(-threshold, threshold)
            previousScroll = currentScroll
            if (currentScroll == 0) {
                expanded = scrollState.maxValue <= 0
                scrollDelta = 0f
            } else if (!expanded && scrollDelta >= threshold) {
                expanded = true
                scrollDelta = 0f
            } else if (expanded && scrollDelta <= -threshold) {
                expanded = false
                scrollDelta = 0f
            }
            expanded
        }
    }

    BackHandler(enabled = !state.isComplete) { }

    ExpressiveScaffold(
        snackbarHost = {
            SnackBarHost(
                hostState = snackBarHost,
                modifier = Modifier.let { if (state.isComplete) it else it.safeDrawingPadding() })
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action)) },
                colors = expressiveTopAppBarColors(),
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    IconButton(onClick = actions.onSaveLog) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.save_log)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.isComplete) {
                SmallExtendedFloatingActionButton(
                    onClick = actions.onClose,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.Close, null) },
                    text = { Text(stringResource(R.string.close)) },
                    modifier = Modifier.padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LaunchedEffect(state.text) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))
            ExecuteStatusHero(state = state)
            ExecuteLogCard(text = state.text)
            if (state.isComplete) {
                ExpressivePrimaryBar(
                    label = stringResource(R.string.close),
                    onClick = actions.onClose,
                    icon = Icons.Filled.Close,
                )
            }
            Spacer(
                Modifier.height(
                    16.dp +
                        (if (state.isComplete) 54.dp else 0.dp) +
                        navBars.calculateBottomPadding() +
                        captionBar.calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
private fun ExecuteStatusHero(state: ExecuteModuleActionUiState) {
    val title: String
    val summary: String?
    val icon: ImageVector
    val containerColor: Color
    when {
        !state.isComplete -> {
            title = stringResource(R.string.action)
            summary = stringResource(R.string.module_action_running_summary)
            icon = Icons.Outlined.PlayArrow
            containerColor = MaterialTheme.colorScheme.primaryContainer
        }
        state.succeeded -> {
            title = stringResource(R.string.module_action_success)
            summary = null
            icon = Icons.Outlined.CheckCircle
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        }
        else -> {
            title = stringResource(R.string.module_action_failed)
            summary = stringResource(R.string.flash_failed_summary)
            icon = Icons.Outlined.Warning
            containerColor = MaterialTheme.colorScheme.errorContainer
        }
    }
    ExpressiveHeroCard(
        title = title,
        summary = summary,
        icon = icon,
        containerColor = containerColor,
    )
}

@Composable
private fun ExecuteLogCard(text: String) {
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
