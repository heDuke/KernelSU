package me.weishu.kernelsu.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class SuperUserFilter(val key: String) {
    Granted("granted"), All("all"), Custom("custom");

    companion object {
        fun fromKey(key: String?) = entries.find { it.key == key } ?: Granted
    }
}

/** Identity presented to the user, including every package sharing the UID. */
@Parcelize
data class AuthorizationIdentity(
    val packageName: String,
    val uid: Int,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
) : Parcelable

@Parcelize
data class AuthorizationTarget(
    val uid: Int,
    val members: Set<AuthorizationIdentity>,
    val wasGranted: Boolean,
) : Parcelable

internal fun AuthorizationTarget.matches(current: Set<AuthorizationIdentity>): Boolean =
    members.isNotEmpty() && members.all { it.uid == uid } && members == current

internal fun canGrantRoot(uid: Int): Boolean = uid >= 0 &&
    (uid % 100000 == 1000 || uid % 100000 >= 2000)

enum class SuperUserFailure {
    Unavailable, TargetChanged, WriteFailed, VerificationFailed, RestrictedUid,
    PolicyChanged, SepolicyFailed, PartialWrite,
    DriverUnavailable, ManagerUnauthorized, InterfaceMismatch, RootUnavailable,
    ServiceTimeout, ServiceQueryFailed,
}

data class SuperUserAccessSnapshot(
    val driverVersion: Int,
    val managerAuthorized: Boolean,
    val kernelUapi: Int,
    val managerUapi: Int,
) {
    val failure: SuperUserFailure? get() = when {
        driverVersion <= 0 -> SuperUserFailure.DriverUnavailable
        !managerAuthorized -> SuperUserFailure.ManagerUnauthorized
        kernelUapi <= 0 || managerUapi <= 0 || kernelUapi != managerUapi -> SuperUserFailure.InterfaceMismatch
        else -> null
    }
}

class SuperUserException(
    val reason: SuperUserFailure,
    val access: SuperUserAccessSnapshot? = null,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause) {
    fun diagnosticReport(): String = buildString {
        appendLine("reason=$reason")
        access?.let {
            appendLine("driver_version=${it.driverVersion}")
            appendLine("manager_authorized=${it.managerAuthorized}")
            appendLine("kernel_uapi=${it.kernelUapi}")
            appendLine("manager_uapi=${it.managerUapi}")
        }
        cause?.let { appendLine("exception=${it.javaClass.simpleName}: ${it.message.orEmpty().take(500)}") }
    }.trimEnd()
}
