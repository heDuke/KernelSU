package me.weishu.kernelsu.ui.screen.envcheck

import me.weishu.kernelsu.data.model.EnvCheckReport

data class EnvCheckScreenActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onCopyReport: (EnvCheckReport) -> Unit,
)
