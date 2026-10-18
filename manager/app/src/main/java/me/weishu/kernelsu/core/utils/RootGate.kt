package me.weishu.kernelsu.core.utils

/** Privileged operations whose availability depends on the loaded kernel interface. */
enum class RootOperation {
    /** Patch or flash a boot/init_boot image with a (newer) LKM. This is the repair path for an outdated kernel. */
    BootPatch,
    /** Install, update or run module scripts; ksud rejects these unless the interfaces match. */
    ModuleMutation,
}

enum class InterfaceRelation { Unknown, Matched, KernelOutdated, ManagerOutdated }

data class RootGateFacts(
    val isManager: Boolean,
    val isSafeMode: Boolean,
    val rootAvailable: Boolean,
    val kernelVersion: Int?,
    val kernelUapi: Int?,
    val managerUapi: Int,
    val minimalKernelVersion: Int = 32513,
) {
    val relation: InterfaceRelation get() = when {
        !isManager || kernelUapi == null -> InterfaceRelation.Unknown
        kernelUapi < managerUapi -> InterfaceRelation.KernelOutdated
        kernelVersion != null && kernelVersion != -1 && kernelVersion < minimalKernelVersion -> InterfaceRelation.KernelOutdated
        kernelUapi > managerUapi -> InterfaceRelation.ManagerOutdated
        else -> InterfaceRelation.Matched
    }
}

sealed interface RootGateResult {
    data object Allowed : RootGateResult
    data object NotManager : RootGateResult
    data object SafeMode : RootGateResult
    data object RootUnavailable : RootGateResult
    data class KernelOutdated(val kernelUapi: Int, val managerUapi: Int) : RootGateResult
    data class ManagerOutdated(val kernelUapi: Int, val managerUapi: Int) : RootGateResult
}

/**
 * Single source of truth for privileged-operation gating.
 * A kernel interface older than the Manager blocks module mutations but must not block
 * flashing a newer LKM, otherwise the device cannot be repaired from the Manager.
 * A kernel interface newer than the Manager always blocks: the Manager must be updated first.
 */
fun evaluateRootGate(operation: RootOperation, facts: RootGateFacts): RootGateResult {
    if (!facts.isManager) return RootGateResult.NotManager
    if (facts.isSafeMode) return RootGateResult.SafeMode
    if (!facts.rootAvailable) return RootGateResult.RootUnavailable
    val kernel = facts.kernelUapi ?: 0
    return when (facts.relation) {
        InterfaceRelation.ManagerOutdated -> RootGateResult.ManagerOutdated(kernel, facts.managerUapi)
        InterfaceRelation.KernelOutdated ->
            if (operation == RootOperation.BootPatch) RootGateResult.Allowed
            else RootGateResult.KernelOutdated(kernel, facts.managerUapi)
        InterfaceRelation.Matched -> RootGateResult.Allowed
        InterfaceRelation.Unknown ->
            if (operation == RootOperation.BootPatch) RootGateResult.Allowed
            else RootGateResult.KernelOutdated(kernel, facts.managerUapi)
    }
}
