package me.weishu.kernelsu.data.repository

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable
import me.weishu.kernelsu.IKsuInterface
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.data.model.AuthorizationTarget
import me.weishu.kernelsu.data.model.SuperUserException
import me.weishu.kernelsu.data.model.SuperUserFailure
import me.weishu.kernelsu.data.model.SuperUserAccessSnapshot
import me.weishu.kernelsu.data.model.canGrantRoot
import me.weishu.kernelsu.data.model.matches
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.KsuService
import me.weishu.kernelsu.ui.util.KsuCli
import me.weishu.kernelsu.ui.util.getSepolicy
import me.weishu.kernelsu.ui.util.setSepolicy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only the active branch is stored in the kernel profile union; rules live in ksud. */
internal fun sameActivePolicy(a: Natives.Profile, b: Natives.Profile): Boolean {
    if (a.name != b.name || a.currentUid != b.currentUid || a.allowSu != b.allowSu) return false
    return if (a.allowSu) {
        a.rootUseDefault == b.rootUseDefault && a.rootTemplate == b.rootTemplate &&
            a.uid == b.uid && a.gid == b.gid && a.groups == b.groups &&
            a.capabilities.toSet() == b.capabilities.toSet() && a.context == b.context &&
            a.namespace == b.namespace && a.flags == b.flags
    } else {
        a.nonRootUseDefault == b.nonRootUseDefault && a.umountModules == b.umountModules
    }
}

class SuperUserRepositoryImpl : SuperUserRepository {

    companion object {
        private const val TAG = "SuperUserRepository"
        private val profileWriteMutex = Mutex()
    }

    private fun readAccess() = SuperUserAccessSnapshot(
        driverVersion = Natives.version, managerAuthorized = Natives.isManager,
        kernelUapi = Natives.kernelUAPIVersion, managerUapi = Natives.managerUAPIVersion,
    )

    private fun ensureAvailable(access: SuperUserAccessSnapshot = readAccess()) {
        access.failure?.let { throw SuperUserException(it, access) }
    }

    override suspend fun getAppList(): Result<Pair<List<AppInfo>, List<Int>>> = withContext(Dispatchers.IO) {
        var access: SuperUserAccessSnapshot? = null
        runCatching {
            val currentAccess = readAccess()
            access = currentAccess
            ensureAvailable(currentAccess)
            val result = connectKsuService {
                Log.w(TAG, "KsuService disconnected")
            }

            var currentBinder = result.first
            var currentConnection = result.second

            try {
                suspend fun reconnect(): IKsuInterface {
                    withContext(Dispatchers.Main) {
                        RootService.unbind(currentConnection)
                    }
                    val retry = connectKsuService { Log.w(TAG, "KsuService disconnected") }
                    currentBinder = retry.first
                    currentConnection = retry.second
                    return IKsuInterface.Stub.asInterface(currentBinder)
                }

                val pm = ksuApp.packageManager
                val start = SystemClock.elapsedRealtime()

                var iface = IKsuInterface.Stub.asInterface(currentBinder)
                val idsArray = try {
                    iface.userIds
                } catch (_: Exception) {
                    iface = reconnect()
                    iface.userIds
                }

                val slice = try {
                    iface.getPackages(0)
                } catch (_: Exception) {
                    iface = reconnect()
                    iface.getPackages(0)
                }

                val packages = slice.list
                if (packages.isEmpty()) throw SuperUserException(SuperUserFailure.ServiceQueryFailed)
                val codeUids = packages.mapNotNull {
                    it.applicationInfo?.takeIf { ai -> ai.flags and ApplicationInfo.FLAG_HAS_CODE != 0 }?.uid
                }.toSet()
                // Include resource-only siblings too: authorization affects the whole UID.
                val newApps = packages.filter { it.applicationInfo?.uid in codeUids }.map {
                    val appInfo = it.applicationInfo!!
                    val profile = Natives.getAppProfile(it.packageName, appInfo.uid)
                    AppInfo(
                        label = appInfo.loadLabel(pm).toString(),
                        packageInfo = it,
                        profile = profile,
                    )
                }

                Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}")
                Pair(newApps, idsArray.toList())
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    RootService.unbind(currentConnection)
                }
            }
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw if (error is SuperUserException) {
                SuperUserException(error.reason, error.access ?: access, error)
            } else SuperUserException(SuperUserFailure.ServiceQueryFailed, access, error)
        }
    }

    override suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureAvailable()
            if (currentApps.isEmpty()) return@runCatching emptyList()

            currentApps.map {
                val profile = Natives.getAppProfile(it.packageName, it.uid)
                it.copy(profile = profile)
            }
        }
    }

    private suspend fun checkedProfile(target: AuthorizationTarget): Natives.Profile {
        val apps = getAppList().getOrThrow().first.filter { it.uid == target.uid }
        if (!target.matches(apps.map { it.authorizationIdentity() }.toSet())) {
            throw SuperUserException(SuperUserFailure.TargetChanged)
        }
        val profile = apps.first { it.packageName == target.members.first().packageName }.profile
            ?: throw SuperUserException(SuperUserFailure.Unavailable)
        if (profile.allowSu != target.wasGranted) throw SuperUserException(SuperUserFailure.PolicyChanged)
        return profile
    }

    override suspend fun setAuthorization(target: AuthorizationTarget, allowed: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                profileWriteMutex.withLock {
                    val current = checkedProfile(target)
                    applyProfile(current.copy(allowSu = allowed))
                }
            }.onFailure { if (it is CancellationException) throw it }
        }

    override suspend fun saveProfile(target: AuthorizationTarget, expected: Natives.Profile,
                                     updated: Natives.Profile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            profileWriteMutex.withLock {
                checkedProfile(target)
                val current = Natives.getAppProfile(expected.name, target.uid)
                if (!sameActivePolicy(current, expected)) throw SuperUserException(SuperUserFailure.PolicyChanged)
                if (updated.name != expected.name || updated.currentUid != target.uid) {
                    throw SuperUserException(SuperUserFailure.TargetChanged)
                }
                if (updated.allowSu && !canGrantRoot(target.uid)) throw SuperUserException(SuperUserFailure.RestrictedUid)
                val ruleKey = updated.rootTemplate ?: updated.name
                val oldKey = expected.rootTemplate ?: expected.name
                val writeRules = updated.allowSu && !updated.rootUseDefault &&
                    (updated.rules != expected.rules || ruleKey != oldKey)
                val oldRules = if (writeRules) getSepolicy(ruleKey) else null
                if (writeRules && ruleKey == oldKey && oldRules != expected.rules) throw SuperUserException(SuperUserFailure.PolicyChanged)
                if (writeRules && !setSepolicy(ruleKey, updated.rules)) {
                    throw SuperUserException(SuperUserFailure.SepolicyFailed)
                }
                try {
                    applyProfile(updated)
                } catch (e: Exception) {
                    if (oldRules != null) {
                        // Restore the persisted file when possible. Applied live SELinux rules
                        // cannot be undone transactionally; still report a partial write.
                        setSepolicy(ruleKey, oldRules)
                        throw SuperUserException(SuperUserFailure.PartialWrite)
                    }
                    throw e
                }
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    private fun applyProfile(updated: Natives.Profile) {
        ensureAvailable()
        if (updated.allowSu && !canGrantRoot(updated.currentUid)) {
            throw SuperUserException(SuperUserFailure.RestrictedUid)
        }
        if (!Natives.setAppProfile(updated)) throw SuperUserException(SuperUserFailure.WriteFailed)
        ensureAvailable()
        if (!sameActivePolicy(Natives.getAppProfile(updated.name, updated.currentUid), updated)) {
            throw SuperUserException(SuperUserFailure.VerificationFailed)
        }
    }

    private suspend fun connectKsuService(
        onDisconnect: () -> Unit = {},
    ): Pair<IBinder, ServiceConnection> = withTimeoutOrNull(15_000L) {
        val shell = withContext(Dispatchers.IO) { KsuCli.SHELL }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceDisconnected(name: ComponentName?) = onDisconnect()

                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (!cont.isActive) return
                        if (binder == null) {
                            RootService.unbind(this)
                            cont.resumeWithException(SuperUserException(SuperUserFailure.ServiceQueryFailed))
                        } else cont.resume(binder to this)
                    }
                }
                cont.invokeOnCancellation {
                    if (Looper.myLooper() == Looper.getMainLooper()) RootService.unbind(connection)
                    else Handler(Looper.getMainLooper()).post { RootService.unbind(connection) }
                }
                val task = RootService.bindOrTask(Intent(ksuApp, KsuService::class.java), Shell.EXECUTOR, connection)
                if (task != null) {
                    if (!shell.isRoot) {
                        RootService.unbind(connection)
                        if (cont.isActive) cont.resumeWithException(SuperUserException(SuperUserFailure.RootUnavailable))
                    } else shell.execTask(task)
                }
            }
        }
    } ?: throw SuperUserException(SuperUserFailure.ServiceTimeout)
}
