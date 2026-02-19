package com.kidshield.tv.service

import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.data.repository.UsageRepository
import javax.inject.Inject

/**
 * Pure business logic for the foreground app monitor.
 * Extracted from [AppMonitorService] for testability.
 *
 * Given the currently detected foreground package, this class decides:
 * - Should the app be blocked (not in allowlist)?
 * - Has the daily time limit been exceeded?
 * - Should usage be recorded?
 */
class AppMonitorLogic @Inject constructor(
    private val appRepository: AppRepository,
    private val usageRepository: UsageRepository,
    private val timeLimitDao: TimeLimitDao
) {
    sealed class MonitorAction {
        /** The foreground app is KidShield itself or a system app — do nothing. */
        data object Ignore : MonitorAction()

        /** The app is not in the allowlist — bring KidShield to foreground. */
        data class BlockNotAllowed(val packageName: String) : MonitorAction()

        /** The daily time limit has been exceeded — suspend + bring KidShield back. */
        data class EnforceTimeLimit(
            val packageName: String,
            val appName: String,
            val minutesUsed: Int,
            val dailyLimit: Int
        ) : MonitorAction()

        /** The app is allowed and within limits — record the usage increment. */
        data class RecordUsage(
            val packageName: String,
            val appName: String,
            val incrementMinutes: Int,
            val newTotalMinutes: Int
        ) : MonitorAction()
    }

    companion object {
        val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.settings",
            "com.google.android.tvlauncher"
        )
    }

    /**
     * Evaluate what action to take for the detected foreground package.
     *
     * @param foregroundPkg the package name detected in the foreground
     * @param ownPackageName this app's package name (com.kidshield.tv)
     * @param pollIntervalMs the polling interval in milliseconds (used to calculate usage increment)
     */
    suspend fun evaluate(
        foregroundPkg: String,
        ownPackageName: String,
        pollIntervalMs: Long
    ): MonitorAction {
        // Own app or system app → ignore
        if (foregroundPkg == ownPackageName) return MonitorAction.Ignore
        if (foregroundPkg in SYSTEM_PACKAGES) return MonitorAction.Ignore

        // Check if the foreground app is in the allowed list
        val app = appRepository.getApp(foregroundPkg)
        if (app == null || !app.isAllowed) {
            return MonitorAction.BlockNotAllowed(foregroundPkg)
        }

        // Check time limit
        val todayUsage = usageRepository.getTodayUsage(foregroundPkg)
        val timeLimit = timeLimitDao.getTimeLimitForAppOnce(foregroundPkg)
        val dailyLimit = timeLimit?.dailyLimitMinutes ?: Int.MAX_VALUE

        return if (todayUsage >= dailyLimit) {
            MonitorAction.EnforceTimeLimit(
                packageName = foregroundPkg,
                appName = app.displayName,
                minutesUsed = todayUsage,
                dailyLimit = dailyLimit
            )
        } else {
            val incrementMinutes = (pollIntervalMs / 60_000).toInt().coerceAtLeast(1)
            usageRepository.recordUsage(foregroundPkg, incrementMinutes)
            MonitorAction.RecordUsage(
                packageName = foregroundPkg,
                appName = app.displayName,
                incrementMinutes = incrementMinutes,
                newTotalMinutes = todayUsage + incrementMinutes
            )
        }
    }
}
