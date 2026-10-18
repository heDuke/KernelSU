package me.weishu.kernelsu.ui.util

import android.content.Context
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.core.utils.RootGateFacts
import me.weishu.kernelsu.core.utils.RootGateResult
import me.weishu.kernelsu.core.utils.RootOperation
import me.weishu.kernelsu.core.utils.evaluateRootGate

/** Reads the live root state. Call off the main thread: [rootAvailable] spawns a shell check. */
fun currentRootGateFacts(checkRoot: Boolean = true): RootGateFacts {
    val isManager = Natives.isManager
    return RootGateFacts(
        isManager = isManager,
        isSafeMode = Natives.isSafeMode,
        rootAvailable = checkRoot && rootAvailable(),
        kernelVersion = if (isManager) Natives.version else null,
        kernelUapi = if (isManager) Natives.kernelUAPIVersion else null,
        managerUapi = Natives.managerUAPIVersion,
        minimalKernelVersion = Natives.MINIMAL_SUPPORTED_KERNEL,
    )
}

fun currentRootGate(operation: RootOperation): RootGateResult = evaluateRootGate(operation, currentRootGateFacts())

/** Localized reason, or null when the operation is allowed. */
fun RootGateResult.message(context: Context): String? = when (this) {
    RootGateResult.Allowed -> null
    RootGateResult.NotManager -> context.getString(R.string.root_gate_not_manager)
    RootGateResult.SafeMode -> context.getString(R.string.root_gate_safe_mode)
    RootGateResult.RootUnavailable -> context.getString(R.string.root_gate_root_unavailable)
    is RootGateResult.KernelOutdated -> context.getString(R.string.root_gate_kernel_outdated, kernelUapi, managerUapi)
    is RootGateResult.ManagerOutdated -> context.getString(R.string.root_gate_manager_outdated, kernelUapi, managerUapi)
}
