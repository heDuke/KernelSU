package me.weishu.kernelsu.core.utils

/** Boot-image patching uses the existing Root shell, not new driver mutation IOCTLs.
 * Target identity, staged hashes and slot checks must already have passed.
 * This exception never grants module/profile writes through an older UAPI.
 */
fun canWriteReviewedBootImage(
    managerAuthorized: Boolean,
    rootAvailable: Boolean,
    safeMode: Boolean,
    kernelUapi: Int,
    managerUapi: Int,
): Boolean = managerAuthorized && rootAvailable && !safeMode && kernelUapi > 0 &&
    (kernelUapi == managerUapi || kernelUapi == 2 && managerUapi == 6)
