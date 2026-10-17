package me.weishu.kernelsu.ui.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.core.utils.ModuleMutationGate
import me.weishu.kernelsu.data.terminal.*
import me.weishu.kernelsu.ui.MainActivity
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit jobs survive page changes; service restart never replays persisted commands. */
class TerminalTaskService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> stopIfIdle() })
    private val queue = ArrayDeque<TerminalTask>()
    private var running: TerminalTask? = null
    private var client: TaskWorkerClient? = null
    private var loop: Job? = null
    private val leased = AtomicBoolean(false)
    private var submissions = 0
    private val pendingStops = mutableSetOf<String>()
    private val pendingIds = mutableSetOf<String>()
    private lateinit var restored: Deferred<Unit>
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.terminal_tasks), NotificationManager.IMPORTANCE_LOW))
        restored = scope.async(Dispatchers.IO) { TerminalTaskRepository.restore() }
    }
    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(getString(R.string.terminal_tasks))
        .setContentText(running?.name ?: getString(R.string.terminal_tasks_waiting, queue.size))
        .setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).setAction("husky.terminal.tasks").putExtra("token", me.weishu.kernelsu.data.repository.SettingsRepositoryImpl().intentToken)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .apply {
            running?.let { task -> addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.terminal_task_stop),
                PendingIntent.getService(this@TerminalTaskService, task.id.hashCode(), Intent(this@TerminalTaskService, TerminalTaskService::class.java)
                    .setAction(STOP).setData(android.net.Uri.parse("husky-task://${task.id}")), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) }
        }.build()
    private fun foreground() {
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION, notification())
    }
    private fun refreshNotification() { getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification()) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action !in setOf(SUBMIT, STOP, INTERRUPT)) { stopSelf(); return START_NOT_STICKY }
        try { foreground() } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            SUBMIT -> {
                val id = intent.getStringExtra("id")
                val command = intent.getStringExtra("command")
                if (id == null || command.isNullOrBlank() || command.length > 16384 || '\u0000' in command) {
                    stopIfIdle()
                    return START_NOT_STICKY
                }
                val task = TerminalTask(id = id, command = command)
                submissions++; pendingIds += id
                scope.launch {
                    try {
                        restored.await()
                        check(Natives.isManager && !Natives.checkUAPIMismatch()) { "Compatible Manager authorization required" }
                        val accepted = withContext(Dispatchers.IO) { TerminalTaskRepository.add(task) }
                        if (accepted) {
                            if (pendingStops.remove(task.id)) withContext(Dispatchers.IO) { TerminalTaskRepository.update(task.id) { it.copy(status = TaskStatus.CANCELED) } }
                            else { queue.addLast(task); startLoop() }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        android.widget.Toast.makeText(this@TerminalTaskService, e.message ?: getString(R.string.terminal_task_failed), android.widget.Toast.LENGTH_LONG).show()
                    } finally { submissions--; pendingIds.remove(id); pendingStops.remove(id); stopIfIdle() }
                }
            }
            STOP, INTERRUPT -> intent.data?.host?.let { id ->
                if (running?.id == id) {
                    if (intent.action == INTERRUPT) client?.interrupt() else {
                        client?.stop()
                        scope.launch(Dispatchers.IO) { TerminalTaskRepository.update(id) { it.copy(status = TaskStatus.STOP_REQUESTED) } }
                    }
                } else {
                    if (id in pendingIds) pendingStops += id
                    val task = queue.firstOrNull { it.id == id }
                    if (task != null) { queue.remove(task); scope.launch(Dispatchers.IO) { TerminalTaskRepository.update(id) { it.copy(status = TaskStatus.CANCELED) } } }
                }
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }
    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            try {
                while (queue.isNotEmpty()) {
                    val task = queue.removeFirst(); running = task
                    val controller = TaskWorkerClient(); client = controller
                    refreshNotification()
                    try {
                        var acquired = false
                        while (!acquired && !controller.stopRequested.get()) {
                            acquired = ModuleMutationGate.tryAcquire()
                            if (!acquired) delay(200)
                        }
                        if (acquired) leased.set(true)
                        if (controller.stopRequested.get()) {
                            withContext(Dispatchers.IO) { TerminalTaskRepository.update(task.id) { it.copy(status = TaskStatus.CANCELED) } }
                            continue
                        }
                        val started = SystemClock.elapsedRealtime()
                        withContext(Dispatchers.IO) {
                            TerminalTaskRepository.reserveLog()
                            TerminalTaskRepository.update(task.id) { it.copy(status = TaskStatus.STARTING, started = System.currentTimeMillis(), startedElapsed = started) }
                            val exit = controller.execute(task.command,
                                onStarted = { identity -> TerminalTaskRepository.update(task.id) { it.copy(identity = identity,
                                    status = if (controller.stopRequested.get()) TaskStatus.STOP_REQUESTED else TaskStatus.RUNNING) } },
                                onOutput = { TerminalTaskRepository.append(task.id, it) })
                            TerminalTaskRepository.update(task.id) { it.copy(code = exit.code.takeIf { code -> code >= 0 }, signal = exit.signal.takeIf { signal -> signal != 0 },
                                elapsed = SystemClock.elapsedRealtime() - started,
                                status = classifyTaskExit(exit, controller.stopRequested.get())) }
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) withContext(Dispatchers.IO) {
                            TerminalTaskRepository.update(task.id) { it.copy(status = TaskStatus.INTERRUPTED, code = null, error = e.message) }
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) {
                            val ended = controller.closeAndWait()
                            // Retain ownership/lease while termination is unconfirmed.
                            if (!ended) {
                                TerminalTaskRepository.update(task.id) { it.copy(status = TaskStatus.STOP_REQUESTED, error = "Worker termination not confirmed") }
                                while (controller.isAlive()) delay(250)
                                TerminalTaskRepository.update(task.id) { it.copy(status = TaskStatus.INTERRUPTED, code = null) }
                            }
                            if (leased.compareAndSet(true, false)) ModuleMutationGate.release()
                        }
                        client = null; running = null
                    }
                }
            } finally { loop = null; stopIfIdle() }
        }
    }
    private fun stopIfIdle() {
        if (queue.isEmpty() && running == null && submissions == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        } else refreshNotification()
    }
    override fun onDestroy() {
        client?.stop()
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "terminal-tasks"
        private const val NOTIFICATION = 64004
        private const val SUBMIT = "terminal.submit"
        private const val STOP = "terminal.stop"
        private const val INTERRUPT = "terminal.interrupt"
        fun submit(context: Context, command: String): String {
            require(command.isNotBlank() && command.length <= 16384 && '\u0000' !in command)
            val id = UUID.randomUUID().toString()
            ContextCompat.startForegroundService(context, Intent(context, TerminalTaskService::class.java).setAction(SUBMIT)
                .putExtra("id", id).putExtra("command", command))
            return id
        }
        fun stop(context: Context, id: String, interrupt: Boolean = false) {
            context.startService(Intent(context, TerminalTaskService::class.java).setAction(if (interrupt) INTERRUPT else STOP)
                .setData(android.net.Uri.parse("husky-task://$id")))
        }
    }
}
