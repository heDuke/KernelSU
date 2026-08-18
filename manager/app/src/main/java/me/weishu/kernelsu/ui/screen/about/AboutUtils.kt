package me.weishu.kernelsu.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutLink(
    val title: String,
    val summary: String,
    val url: String,
)

object AboutLinks {
    const val HUSKY_SOURCE = "https://github.com/heDuke/KernelSU"
    const val KERNELSU = "https://github.com/tiann/KernelSU"
    const val KERNEL_ASSISTED_SUPERUSER = "https://git.zx2c4.com/kernel-assisted-superuser/about/"
    const val MAGISK = "https://github.com/topjohnwu/Magisk"
    const val GENUINE = "https://github.com/brevent/genuine/"
    const val DIAMORPHINE = "https://github.com/m0nad/Diamorphine"
    const val PACKAGE_NAME = "me.weishu.kernelsu.husky.fork"
}
