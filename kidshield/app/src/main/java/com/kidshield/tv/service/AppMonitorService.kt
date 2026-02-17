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
import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.data.repository.UsageRepository
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

    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var timeLimitDao: TimeLimitDao
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

        scope.launch {
            while (isActive) {
                checkForegroundApp()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Detect which package is in the foreground.
     * Uses ActivityManager first (more reliable for current task),
     * falls back to UsageStatsManager.
     */
    private fun detectForegroundPackage(): String? {
        // Method 1: ActivityManager — get top running task
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(1)
            if (!tasks.isNullOrEmpty()) {
                val topPkg = tasks[0].topActivity?.packageName
                if (topPkg != null) return topPkg
            }
        } catch (_: Exception) { }

        // Method 2: UsageStatsManager — fallback
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000,
            now
        )
        return stats
            ?.filter { it.totalTimeInForeground > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private suspend fun checkForegroundApp() {
        val foregroundPkg = detectForegroundPackage() ?: return

        // If foreground is our own app, nothing to do
        if (foregroundPkg == packageName) return

        // System apps we should ignore
        val systemPackages = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.settings",
            "com.google.android.tvlauncher"
        )
        if (foregroundPkg in systemPackages) return

        // Check if the foreground app is in the allowed list
        val app = appRepository.getApp(foregroundPkg)
        if (app == null || !app.isAllowed) {
            Log.d(TAG, "Non-allowed app in foreground: $foregroundPkg — pulling back")
            bringKidShieldToForeground()
            return
        }

        // Check time limit
        val todayUsage = usageRepository.getTodayUsage(foregroundPkg)
        val timeLimit = timeLimitDao.getTimeLimitForAppOnce(foregroundPkg)
        val dailyLimit = timeLimit?.dailyLimitMinutes ?: Int.MAX_VALUE

        if (todayUsage >= dailyLimit) {
            Log.d(TAG, "Time limit reached for ${app.displayName} ($todayUsage >= $dailyLimit)")
            lockTaskHelper.suspendPackage(foregroundPkg)
            bringKidShieldToForeground()

            val broadcastIntent = Intent(ACTION_TIME_UP).apply {
                setPackage(packageName)
                putExtra(EXTRA_PACKAGE, foregroundPkg)
                putExtra(EXTRA_APP_NAME, app.displayName)
            }
            sendBroadcast(broadcastIntent)
        } else {
            // Record usage increment
            val incrementMinutes = (POLL_INTERVAL_MS / 60_000).toInt().coerceAtLeast(1)
            usageRepository.recordUsage(foregroundPkg, incrementMinutes)
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
