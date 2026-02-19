package com.kidshield.tv.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kidshield.tv.MainActivity
import com.kidshield.tv.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppMonitorService : Service() {

    @Inject lateinit var monitorLogic: AppMonitorLogic
    @Inject lateinit var lockTaskHelper: LockTaskHelper

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false

    companion object {
        private const val TAG = "KidShield.Monitor"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "kidshield_monitor"
        private const val POLL_INTERVAL_MS = 10_000L  // 10s for snappy enforcement
        const val ACTION_TIME_UP = "com.kidshield.tv.TIME_UP"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_APP_NAME = "appName"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.d(TAG, "Monitor loop started, polling every ${POLL_INTERVAL_MS}ms")

        scope.launch {
            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in checkForegroundApp", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Detect which package is in the foreground.
     *
     * Uses UsageEvents as the primary method — this is the most reliable
     * approach in lock task mode, where ActivityManager.getRunningTasks()
     * only returns the lock task root (KidShield) even when another
     * allowed app is visually in the foreground.
     *
     * Falls back to ActivityManager for non-lock-task scenarios.
     */
    private fun detectForegroundPackage(): String? {
        // Method 1: UsageEvents — find the currently-resumed activity.
        // We scan events over the last 10 minutes. The last ACTIVITY_RESUMED
        // event that has no subsequent ACTIVITY_PAUSED for the same package
        // is the package currently in the foreground.
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val events = usm.queryEvents(now - 600_000, now) // last 10 minutes
                var lastResumedPkg: String? = null
                val event = android.app.usage.UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                            lastResumedPkg = event.packageName
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                            // If the paused package is the last resumed, it's no longer foreground
                            if (event.packageName == lastResumedPkg) {
                                lastResumedPkg = null
                            }
                        }
                    }
                }
                if (lastResumedPkg != null) {
                    Log.d(TAG, "UsageEvents detected foreground: $lastResumedPkg")
                    return lastResumedPkg
                } else {
                    Log.d(TAG, "UsageEvents: no currently-resumed activity found in last 10min")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "UsageEvents query failed", e)
        }

        // Method 2: ActivityManager — fallback for non-lock-task mode
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(1)
            if (!tasks.isNullOrEmpty()) {
                val topPkg = tasks[0].topActivity?.packageName
                if (topPkg != null) {
                    Log.d(TAG, "ActivityManager detected foreground: $topPkg")
                    return topPkg
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ActivityManager failed", e)
        }

        // Method 3: UsageStatsManager aggregate — last resort
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 60_000,
                    now
                )
                val result = stats
                    ?.filter { it.totalTimeInForeground > 0 }
                    ?.maxByOrNull { it.lastTimeUsed }
                    ?.packageName
                Log.d(TAG, "UsageStats aggregate detected: $result")
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "UsageStats aggregate failed", e)
        }

        return null
    }

    private suspend fun checkForegroundApp() {
        val foregroundPkg = detectForegroundPackage()
        if (foregroundPkg == null) {
            Log.d(TAG, "No foreground package detected")
            return
        }

        val action = monitorLogic.evaluate(foregroundPkg, packageName, POLL_INTERVAL_MS)

        when (action) {
            is AppMonitorLogic.MonitorAction.Ignore -> {
                // KidShield or system app — no action needed
            }
            is AppMonitorLogic.MonitorAction.BlockNotAllowed -> {
                Log.d(TAG, "Non-allowed app in foreground: ${action.packageName} — pulling back")
                bringKidShieldToForeground()
            }
            is AppMonitorLogic.MonitorAction.EnforceTimeLimit -> {
                Log.d(TAG, "TIME LIMIT REACHED for ${action.appName} " +
                    "(${action.minutesUsed} >= ${action.dailyLimit}) — enforcing!")
                lockTaskHelper.suspendPackage(action.packageName)
                bringKidShieldToForeground()

                val broadcastIntent = Intent(ACTION_TIME_UP).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_PACKAGE, action.packageName)
                    putExtra(EXTRA_APP_NAME, action.appName)
                }
                sendBroadcast(broadcastIntent)
            }
            is AppMonitorLogic.MonitorAction.RecordUsage -> {
                Log.d(TAG, "Recorded ${action.incrementMinutes}min usage for " +
                    "${action.appName}, total now ~${action.newTotalMinutes}min")
            }
        }
    }

    private fun bringKidShieldToForeground() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "KidShield Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors app usage for parental controls"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitoring_notification_title))
            .setContentText(getString(R.string.monitoring_notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        isMonitoring = false
        super.onDestroy()
    }
}
