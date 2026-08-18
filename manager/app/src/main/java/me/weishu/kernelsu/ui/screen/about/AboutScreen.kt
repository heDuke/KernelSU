package me.weishu.kernelsu.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        packageName = AboutLinks.PACKAGE_NAME,
        blurb = stringResource(R.string.about_blurb),
        source = AboutLink(
            title = stringResource(R.string.about_source),
            summary = stringResource(R.string.about_source_summary),
            url = AboutLinks.HUSKY_SOURCE,
        ),
        creditsTitle = stringResource(R.string.about_credits),
        credits = listOf(
            AboutLink(
                title = stringResource(R.string.about_credit_kernelsu),
                summary = stringResource(R.string.about_credit_kernelsu_summary),
                url = AboutLinks.KERNELSU,
            ),
            AboutLink(
                title = stringResource(R.string.about_credit_kas),
                summary = stringResource(R.string.about_credit_kas_summary),
                url = AboutLinks.KERNEL_ASSISTED_SUPERUSER,
            ),
            AboutLink(
                title = stringResource(R.string.about_credit_magisk),
                summary = stringResource(R.string.about_credit_magisk_summary),
                url = AboutLinks.MAGISK,
            ),
            AboutLink(
                title = stringResource(R.string.about_credit_genuine),
                summary = stringResource(R.string.about_credit_genuine_summary),
                url = AboutLinks.GENUINE,
            ),
            AboutLink(
                title = stringResource(R.string.about_credit_diamorphine),
                summary = stringResource(R.string.about_credit_diamorphine_summary),
                url = AboutLinks.DIAMORPHINE,
            ),
        ),
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenLink = uriHandler::openUri,
    )

    AboutScreenMaterial(state, actions)
}
