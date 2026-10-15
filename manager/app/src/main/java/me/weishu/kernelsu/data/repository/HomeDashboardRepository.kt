package me.weishu.kernelsu.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.*
import me.weishu.kernelsu.ksuApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only app-private presentation preferences; never changes the boot/loader selection. */
object HomeDashboardPreferences {
    private val prefs by lazy { ksuApp.getSharedPreferences("home-dashboard", Context.MODE_PRIVATE) }
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    data class Value(val layout: DashboardLayout, val raw: String?, val invalid: Boolean = false)
    private fun read(): Value {
        val raw = prefs.getString("layout", null)
        return runCatching { Value(raw?.let(DashboardLayout::parse) ?: DashboardLayout(), raw) }
            .getOrElse { Value(DashboardLayout(), raw, true) }
    }
    suspend fun save(expected: String?, layout: DashboardLayout) = withContext(Dispatchers.IO) {
        synchronized(this@HomeDashboardPreferences) {
            check(prefs.getString("layout", null) == expected) { "Dashboard changed; reopen the editor" }
            val serialized = layout.serialize()
            DashboardLayout.parse(serialized)
            check(prefs.edit().putString("layout", serialized).commit()) { "Cannot save dashboard" }
            mutable.value = Value(layout, serialized)
        }
    }
}

class HomeRuntimeRepository {
    private val prefs = ksuApp.getSharedPreferences("home-runtime", Context.MODE_PRIVATE)
    var error: String? = null
        private set
    @Volatile var record: RuntimeRecord = runCatching {
        prefs.getString("record", null)?.let { value ->
            require(value.length <= 32_768)
            val json = JSONObject(value)
            require(json.getInt("schema") == 1)
            val array = json.getJSONArray("events"); require(array.length() <= 40)
            RuntimeRecord(boot = json.getString("boot"), lastElapsed = json.getLong("elapsed").also { require(it >= 0) },
                startedAt = json.optLong("started", 0).takeIf { it > 0 },
                // A new observer cannot prove continuity during a process gap.
                seenActive = json.getBoolean("seen"),
                activeFaults = json.getJSONArray("faults").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
                total = json.getLong("total").also { require(it >= 0) },
                events = (0 until array.length()).map { i -> array.getJSONObject(i).let { RuntimeIncident(it.getLong("at"), it.getString("code")) } })
        } ?: RuntimeRecord()
    }.getOrElse { error = it.message; RuntimeRecord() }
        private set

    private var observing = false
    @Synchronized fun start() { observing = true }

    @Synchronized fun observe(active: Boolean, faults: Set<String>): RuntimeRecord {
        if (!observing) return record
        val boot = runCatching {
            Settings.Global.getInt(ksuApp.contentResolver, Settings.Global.BOOT_COUNT).toString()
        }.getOrElse { runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("") }
        if (error != null) return record // Preserve corrupt evidence until explicitly repaired.
        record = record.observe(boot, SystemClock.elapsedRealtime(), System.currentTimeMillis(), active, faults)
        val json = JSONObject().put("schema", 1).put("boot", record.boot).put("elapsed", record.lastElapsed)
            .put("started", record.startedAt).put("seen", record.seenActive).put("total", record.total)
            .put("faults", JSONArray(record.activeFaults.toList()))
            .put("events", JSONArray(record.events.map { JSONObject().put("at", it.at).put("code", it.code) }))
        if (!prefs.edit().putString("record", json.toString()).commit()) error = "Cannot save observations"
        return record
    }
    @Synchronized fun pause() { observing = false; record = record.pause() }
}
