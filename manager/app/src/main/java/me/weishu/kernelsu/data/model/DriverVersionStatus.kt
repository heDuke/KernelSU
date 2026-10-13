package me.weishu.kernelsu.data.model

enum class DriverVersionStatus { Unavailable, Unknown, Matched, BuildDifferent, Incompatible }

/** Build counters are not the userspace/kernel interface contract. */
fun driverVersionStatus(
    managerBuild: Long,
    driverBuild: Int?,
    managerUapi: Int,
    driverUapi: Int?,
    requiresNewKernel: Boolean,
): DriverVersionStatus = when {
    driverBuild == null -> DriverVersionStatus.Unavailable
    requiresNewKernel -> DriverVersionStatus.Incompatible
    managerUapi <= 0 || driverUapi == null || driverUapi <= 0 -> DriverVersionStatus.Unknown
    managerUapi != driverUapi -> DriverVersionStatus.Incompatible
    managerBuild == driverBuild.toLong() -> DriverVersionStatus.Matched
    else -> DriverVersionStatus.BuildDifferent
}
