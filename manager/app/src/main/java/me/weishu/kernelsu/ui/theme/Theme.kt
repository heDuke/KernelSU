package me.weishu.kernelsu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl

enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromValue(value: Int) = when (value) {
            1, 4 -> LIGHT
            2, 5, 6 -> DARK
            else -> SYSTEM
        }
    }

    val isSystem: Boolean get() = this == SYSTEM
    val isDark: Boolean get() = this == DARK
}

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
)

val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
            this == PaletteStyle.Neutral ||
            this == PaletteStyle.Vibrant ||
            this == PaletteStyle.Expressive

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

object ThemeController {
    fun getAppSettings(repo: SettingsRepository = SettingsRepositoryImpl()): AppSettings {
        migrateAppearance(repo)
        return AppSettings(
            colorMode = ColorMode.fromValue(repo.themeMode),
            keyColor = if (repo.keyColor == 0) 0 else HuskySeedColorArgb,
            paletteStyle = PaletteStyle.TonalSpot,
            colorSpec = ColorSpec.SpecVersion.SPEC_2025,
        )
    }

    private fun migrateAppearance(repo: SettingsRepository) {
        val rawMode = repo.themeMode
        val migratedMode = ColorMode.fromValue(rawMode).value
        val wasMonet = rawMode in 3..5
        if (wasMonet) {
            if (repo.keyColor != 0) repo.keyColor = 0
        } else if (repo.keyColor != 0 && repo.keyColor != HuskySeedColorArgb) {
            repo.keyColor = HuskySeedColorArgb
        }
        if (rawMode != migratedMode) {
            repo.themeMode = migratedMode
        }
    }
}

@Composable
fun KernelSUTheme(
    appSettings: AppSettings = ThemeController.getAppSettings(),
    content: @Composable () -> Unit
) {
    MaterialKernelSUTheme(
        appSettings = appSettings,
        content = content
    )
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        ColorMode.LIGHT.value -> false
        ColorMode.DARK.value -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalColorMode = staticCompositionLocalOf { 0 }

val LocalEnableBlur = staticCompositionLocalOf { false }

val LocalEnableNavigationBadge = staticCompositionLocalOf { true }
