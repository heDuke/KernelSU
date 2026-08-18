package me.weishu.kernelsu.data.repository

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.data.model.EnvCheckGroup
import me.weishu.kernelsu.data.model.EnvCheckItem
import me.weishu.kernelsu.data.model.EnvCheckReport
import me.weishu.kernelsu.data.model.EnvCheckSeverity
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.home.getManagerVersion
import me.weishu.kernelsu.ui.util.getSystemProperty
import me.weishu.kernelsu.ui.util.isAbDevice
import me.weishu.kernelsu.ui.util.isNetworkAvailable
import org.json.JSONObject

class EnvCheckRepositoryImpl(
    private val moduleRepo: ModuleRepository = ModuleRepositoryImpl(),
    private val recommendedRepo: RecommendedModuleRepository = RecommendedModuleRepositoryImpl(),
    private val huskyRepo: HuskyReleaseRepository = HuskyReleaseRepositoryImpl(),
    private val integrityChecker: PlayIntegrityChecker = StubPlayIntegrityChecker(),
) : EnvCheckRepository {

    override suspend fun runCheck(): Result<EnvCheckReport> = withContext(Dispatchers.IO) {
        runCatching {
            val items = mutableListOf<EnvCheckItem>()
            val modules = moduleRepo.getModules().getOrDefault(emptyList())
            val recommended = recommendedRepo.getRecommendedModules().getOrDefault(emptyList())
            val rules = loadModuleRules()

            items += rootItems()
            items += versionItems()
            items += bootItems()
            items += integrityOnlineItem()
            items += moduleItems(modules, recommended, rules)
            items += systemItems()

            EnvCheckReport(
                items = items,
                generatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private fun rootItems(): List<EnvCheckItem> {
        val ksuVersion = runCatching { Natives.version }.getOrNull()?.takeIf { it > 0 }
        val kernel = getKernelVersion()
        return buildList {
            when {
                ksuVersion != null -> add(
                    item(
                        id = "root_working",
                        group = EnvCheckGroup.Root,
                        title = "KernelSU",
                        detail = "Working (version $ksuVersion)",
                        severity = EnvCheckSeverity.Pass,
                        raw = ksuVersion.toString(),
                    )
                )
                kernel.isGKI() -> add(
                    item(
                        id = "root_not_installed",
                        group = EnvCheckGroup.Root,
                        title = "KernelSU",
                        detail = "Not installed on a GKI kernel",
                        severity = EnvCheckSeverity.Fail,
                    )
                )
                else -> add(
                    item(
                        id = "root_unsupported",
                        group = EnvCheckGroup.Root,
                        title = "KernelSU",
                        detail = "Unsupported kernel for LKM install",
                        severity = EnvCheckSeverity.Fail,
                    )
                )
            }
            if (ksuVersion != null) {
                val lkm = runCatching { Natives.isLkmMode }.getOrNull()
                add(
                    item(
                        id = "root_mode",
                        group = EnvCheckGroup.Root,
                        title = "Working mode",
                        detail = when (lkm) {
                            true -> "LKM"
                            false -> "GKI"
                            null -> "Unknown"
                        },
                        severity = if (lkm == false) EnvCheckSeverity.Warn else EnvCheckSeverity.Pass,
                        raw = lkm?.toString().orEmpty(),
                    )
                )
                if (runCatching { Natives.isSafeMode }.getOrDefault(false)) {
                    add(
                        item(
                            id = "root_safe",
                            group = EnvCheckGroup.Root,
                            title = "Safe mode",
                            detail = "Safe mode is active",
                            severity = EnvCheckSeverity.Warn,
                        )
                    )
                }
                if (runCatching { Natives.isLateLoadMode }.getOrDefault(false)) {
                    add(
                        item(
                            id = "root_jailbreak",
                            group = EnvCheckGroup.Root,
                            title = "Jailbreak mode",
                            detail = "Late-load / jailbreak mode is active",
                            severity = EnvCheckSeverity.Warn,
                        )
                    )
                }
            }
        }
    }

    private suspend fun versionItems(): List<EnvCheckItem> {
        val manager = getManagerVersion(ksuApp)
        val ksuVersion = runCatching { Natives.version }.getOrNull()?.takeIf { it > 0 }
        val items = mutableListOf<EnvCheckItem>()
        if (ksuVersion != null) {
            val match = ksuVersion.toLong() == manager.versionCode
            items += item(
                id = "version_match",
                group = EnvCheckGroup.Version,
                title = "Manager ↔ driver",
                detail = if (match) {
                    "Matched (${manager.versionCode})"
                } else {
                    "Mismatch: manager ${manager.versionCode}, driver $ksuVersion"
                },
                severity = if (match) EnvCheckSeverity.Pass else EnvCheckSeverity.Fail,
                raw = "manager=${manager.versionCode},driver=$ksuVersion",
            )
        }
        if (runCatching { Natives.isManager }.getOrDefault(false) &&
            runCatching { Natives.isPrBuild }.getOrDefault(false)
        ) {
            items += item(
                id = "version_pr_kernel",
                group = EnvCheckGroup.Version,
                title = "Production signing",
                detail = "Kernel reports PR dual-sign support (non-production)",
                severity = EnvCheckSeverity.Warn,
            )
        }
        if (isNetworkAvailable(ksuApp)) {
            huskyRepo.fetchLatest().fold(
                onSuccess = { release ->
                    val current = ksuVersion?.toLong()
                    val upToDate = current != null && current >= release.versionCode
                    items += item(
                        id = "version_husky_lkm",
                        group = EnvCheckGroup.Version,
                        title = "Husky LKM release",
                        detail = if (upToDate) {
                            "Up to date (${release.tag})"
                        } else {
                            "Update available: ${release.tag}"
                        },
                        severity = if (upToDate) EnvCheckSeverity.Pass else EnvCheckSeverity.Warn,
                        raw = release.tag,
                    )
                },
                onFailure = { e ->
                    items += item(
                        id = "version_husky_lkm",
                        group = EnvCheckGroup.Version,
                        title = "Husky LKM release",
                        detail = e.message ?: "Failed to fetch releases",
                        severity = EnvCheckSeverity.Unknown,
                    )
                },
            )
        } else {
            items += item(
                id = "version_husky_lkm",
                group = EnvCheckGroup.Version,
                title = "Husky LKM release",
                detail = "Offline — skipped release check",
                severity = EnvCheckSeverity.Unknown,
            )
        }
        return items
    }

    private suspend fun bootItems(): List<EnvCheckItem> {
        val flashLocked = firstProp(
            "ro.boot.flash.locked",
            "ro.boot.vbmeta.device_state",
        )
        val oemUnlock = getSystemProperty("sys.oem_unlock_allowed")
        val verified = firstProp(
            "ro.boot.verifiedbootstate",
            "ro.boot.veritymode",
        )
        val vbmeta = getSystemProperty("ro.boot.vbmeta.device_state")

        val blSeverity: EnvCheckSeverity
        val blDetail: String
        when {
            flashLocked.equals("1", true) || flashLocked.equals("locked", true) -> {
                blSeverity = EnvCheckSeverity.Pass
                blDetail = "Bootloader appears locked ($flashLocked)"
            }
            flashLocked.equals("0", true) || flashLocked.equals("unlocked", true) ||
                verified.equals("orange", true) -> {
                blSeverity = EnvCheckSeverity.Warn
                blDetail = "Bootloader unlocked / orange state (common on rooted devices)"
            }
            flashLocked.isBlank() && oemUnlock.isBlank() && verified.isBlank() -> {
                blSeverity = EnvCheckSeverity.Unknown
                blDetail = "Could not read bootloader lock props"
            }
            else -> {
                blSeverity = EnvCheckSeverity.Unknown
                blDetail = "Raw lock/state: flash.locked=$flashLocked oem_unlock=$oemUnlock verified=$verified"
            }
        }

        val avbSeverity: EnvCheckSeverity
        val avbDetail: String
        when {
            verified.isBlank() && vbmeta.isBlank() -> {
                avbSeverity = EnvCheckSeverity.Unknown
                avbDetail = "Verified Boot props unavailable"
            }
            verified.equals("green", true) -> {
                avbSeverity = EnvCheckSeverity.Pass
                avbDetail = "Verified Boot green"
            }
            verified.equals("orange", true) || verified.equals("yellow", true) -> {
                avbSeverity = EnvCheckSeverity.Warn
                avbDetail = "Verified Boot $verified (expected after unlock/custom images)"
            }
            verified.equals("red", true) -> {
                avbSeverity = EnvCheckSeverity.Fail
                avbDetail = "Verified Boot red"
            }
            else -> {
                avbSeverity = EnvCheckSeverity.Unknown
                avbDetail = "verifiedbootstate=$verified vbmeta=$vbmeta"
            }
        }

        val ab = isAbDevice()
        return listOf(
            item(
                id = "boot_bl",
                group = EnvCheckGroup.Boot,
                title = "Bootloader (BL)",
                detail = blDetail,
                severity = blSeverity,
                raw = "flash.locked=$flashLocked oem_unlock=$oemUnlock",
            ),
            item(
                id = "boot_avb",
                group = EnvCheckGroup.Boot,
                title = "Local integrity (AVB)",
                detail = avbDetail,
                severity = avbSeverity,
                raw = "verifiedbootstate=$verified vbmeta=$vbmeta",
            ),
            item(
                id = "boot_ab",
                group = EnvCheckGroup.Boot,
                title = "A/B slots",
                detail = if (ab) "A/B device — inactive-slot install available after OTA" else "Not detected as A/B",
                severity = EnvCheckSeverity.Pass,
                raw = ab.toString(),
            ),
        )
    }

    private suspend fun integrityOnlineItem(): EnvCheckItem {
        val result = integrityChecker.check()
        return item(
            id = "integrity_play",
            group = EnvCheckGroup.IntegrityOnline,
            title = "Play Integrity",
            detail = result.summary,
            severity = when {
                !result.available -> EnvCheckSeverity.Unknown
                result.summary.contains("MEETS_", ignoreCase = true) &&
                    !result.summary.contains("STRONG", ignoreCase = true) &&
                    result.summary.contains("BASIC", ignoreCase = true) -> EnvCheckSeverity.Warn
                result.summary.contains("MEETS_DEVICE", ignoreCase = true) ||
                    result.summary.contains("MEETS_STRONG", ignoreCase = true) -> EnvCheckSeverity.Pass
                else -> EnvCheckSeverity.Warn
            },
            raw = result.raw,
        )
    }

    private fun moduleItems(
        modules: List<Module>,
        recommended: List<me.weishu.kernelsu.data.model.RecommendedModule>,
        rules: ModuleRules,
    ): List<EnvCheckItem> {
        val active = modules.filter { !it.remove }
        fun matches(module: Module, ids: Set<String>, keywords: List<String>): Boolean {
            val id = module.id.lowercase()
            val name = module.name.lowercase()
            if (id in ids) return true
            return keywords.any { name.contains(it) || id.contains(it.replace(" ", "")) }
        }

        val hasZygisk = active.any { matches(it, rules.zygiskIds, rules.zygiskKeywords) }
        val hasLsposed = active.any { matches(it, rules.lsposedIds, rules.lsposedKeywords) }
        val unpinHits = active.filter { matches(it, rules.sslUnpinIds, rules.sslUnpinKeywords) }

        val items = mutableListOf<EnvCheckItem>()
        items += item(
            id = "mod_zygisk",
            group = EnvCheckGroup.Modules,
            title = "Zygisk provider",
            detail = if (hasZygisk) "Installed" else "Not found (needed for Zygisk LSPosed)",
            severity = if (hasZygisk) EnvCheckSeverity.Pass else EnvCheckSeverity.Warn,
        )
        items += item(
            id = "mod_lsposed",
            group = EnvCheckGroup.Modules,
            title = "LSPosed",
            detail = when {
                hasLsposed && hasZygisk -> "Installed with Zygisk provider"
                hasLsposed && !hasZygisk -> "Installed but Zygisk provider missing"
                else -> "Not installed"
            },
            severity = when {
                hasLsposed && !hasZygisk -> EnvCheckSeverity.Fail
                hasLsposed -> EnvCheckSeverity.Pass
                else -> EnvCheckSeverity.Warn
            },
        )
        recommended.forEach { rec ->
            val installed = active.any { it.id == rec.id }
            if (!installed) {
                items += item(
                    id = "mod_rec_${rec.id}",
                    group = EnvCheckGroup.Modules,
                    title = "Recommended: ${rec.name}",
                    detail = "Not installed",
                    severity = EnvCheckSeverity.Warn,
                    raw = rec.id,
                )
            }
        }
        items += item(
            id = "mod_ssl_unpin",
            group = EnvCheckGroup.Modules,
            title = "Certificate pinning related modules",
            detail = if (unpinHits.isEmpty()) {
                "No known SSL-unpin / MITM helper modules detected"
            } else {
                "Found: " + unpinHits.joinToString { it.name.ifBlank { it.id } } +
                    " — may affect banking apps that pin certificates"
            },
            severity = if (unpinHits.isEmpty()) EnvCheckSeverity.Pass else EnvCheckSeverity.Warn,
            raw = unpinHits.joinToString(",") { it.id },
        )
        return items
    }

    private fun systemItems(): List<EnvCheckItem> {
        val selinux = firstProp("ro.build.selinux", "ro.boot.selinux").ifBlank {
            // best-effort; Home also reads via native helpers when available
            "unknown"
        }
        return listOf(
            item(
                id = "sys_model",
                group = EnvCheckGroup.System,
                title = "Device",
                detail = "${Build.MANUFACTURER} ${Build.MODEL}",
                severity = EnvCheckSeverity.Pass,
                raw = Build.FINGERPRINT,
            ),
            item(
                id = "sys_network",
                group = EnvCheckGroup.System,
                title = "Network",
                detail = if (isNetworkAvailable(ksuApp)) "Available" else "Offline",
                severity = if (isNetworkAvailable(ksuApp)) EnvCheckSeverity.Pass else EnvCheckSeverity.Warn,
            ),
            item(
                id = "sys_selinux_prop",
                group = EnvCheckGroup.System,
                title = "SELinux prop",
                detail = selinux,
                severity = EnvCheckSeverity.Unknown,
                raw = selinux,
            ),
        )
    }

    private fun loadModuleRules(): ModuleRules {
        return runCatching {
            val json = ksuApp.assets.open(RULES_ASSET).bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            ModuleRules(
                zygiskIds = obj.optJSONArray("zygisk_ids").toStringSet(),
                zygiskKeywords = obj.optJSONArray("zygisk_name_keywords").toStringList(),
                lsposedIds = obj.optJSONArray("lsposed_ids").toStringSet(),
                lsposedKeywords = obj.optJSONArray("lsposed_name_keywords").toStringList(),
                sslUnpinIds = obj.optJSONArray("ssl_unpin_ids").toStringSet(),
                sslUnpinKeywords = obj.optJSONArray("ssl_unpin_name_keywords").toStringList(),
            )
        }.getOrElse { ModuleRules.DEFAULT }
    }

    private fun firstProp(vararg keys: String): String {
        for (key in keys) {
            val v = getSystemProperty(key)
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun item(
        id: String,
        group: EnvCheckGroup,
        title: String,
        detail: String,
        severity: EnvCheckSeverity,
        raw: String = "",
    ) = EnvCheckItem(id, group, title, detail, severity, raw)

    private data class ModuleRules(
        val zygiskIds: Set<String>,
        val zygiskKeywords: List<String>,
        val lsposedIds: Set<String>,
        val lsposedKeywords: List<String>,
        val sslUnpinIds: Set<String>,
        val sslUnpinKeywords: List<String>,
    ) {
        companion object {
            val DEFAULT = ModuleRules(
                zygiskIds = setOf("zygisksu"),
                zygiskKeywords = listOf("zygisknext", "zygisk next"),
                lsposedIds = setOf("lsposed"),
                lsposedKeywords = listOf("lsposed"),
                sslUnpinIds = setOf("trustmealready", "justtrustme"),
                sslUnpinKeywords = listOf("ssl unpin", "justtrust", "trustme"),
            )
        }
    }

    companion object {
        private const val RULES_ASSET = "env_check_modules.json"
    }
}

private fun org.json.JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (i in 0 until length()) {
            optString(i)?.lowercase()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            optString(i)?.lowercase()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}
