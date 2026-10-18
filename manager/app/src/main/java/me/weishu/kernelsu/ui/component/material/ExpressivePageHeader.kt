package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Native Material 3 header. Share [scrollBehavior] with the content's nested-scroll
 * connection so the app bar measures its collapse range before consuming gestures.
 * The Activity's NoActionBar theme removes the separate system title bar.
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
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    LargeFlexibleTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = { navigationIcon?.invoke() },
        actions = { actions?.invoke() },
        colors = expressiveTopAppBarColors(containerColor = containerColor),
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
    )
}
