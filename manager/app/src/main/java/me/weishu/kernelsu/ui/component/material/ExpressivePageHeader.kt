package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Single-block page header: status-bar insets + large title (+ optional nav/actions).
 *
 * Unlike [androidx.compose.material3.LargeFlexibleTopAppBar] (TwoRowsTopAppBar), this does
 * **not** reserve a collapsed-height top strip above the large title. That empty/actions
 * strip was showing on every tab as a duplicated small title bar.
 */
@Composable
fun ExpressivePageHeader(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    ),
) {
    Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        ) {
            val hasChrome = navigationIcon != null || actions != null
            if (hasChrome) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (navigationIcon != null) {
                        Box { navigationIcon() }
                    }
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.headlineMedium,
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        ) { title() }
                    }
                    if (actions != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
                    }
                }
            } else {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.headlineMedium,
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    ) { title() }
                }
            }
        }
    }
}
