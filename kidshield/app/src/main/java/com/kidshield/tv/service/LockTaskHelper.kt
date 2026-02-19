package com.kidshield.tv.service

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import com.kidshield.tv.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockTaskHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = KidShieldDeviceAdminReceiver.getComponentName(context)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    val isAdminActive: Boolean
        get() = dpm.isAdminActive(adminComponent)

    /**
     * Allowlist packages that can run in lock task mode.
     * Only works if the app is Device Owner.
     */
    fun setAllowedLockTaskPackages(packages: List<String>) {
        if (!isDeviceOwner) return
        val allPackages = (packages + context.packageName).distinct().toTypedArray()
        dpm.setLockTaskPackages(adminComponent, allPackages)
    }

    /**
     * Configure lock task features when in Device Owner mode.
     * Enables Home button and overview/recents so kids can return to KidShield.
     * Disables status bar, notifications, and global actions.
     *
     * Also registers KidShield as the persistent preferred Home activity
     * via DPM so the Home button always returns here.
     */
    fun configureLockTaskFeatures() {
        if (!isDeviceOwner) return

        // Enable Home button so kids can always get back to KidShield
        dpm.setLockTaskFeatures(
            adminComponent,
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
            DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
        )

        // Register KidShield as the preferred Home activity so the
        // Home button in lock task mode always launches us
        try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val activity = ComponentName(context, MainActivity::class.java)
            dpm.addPersistentPreferredActivity(adminComponent, filter, activity)
            Log.d("KidShield", "Set as persistent preferred home activity")
        } catch (e: Exception) {
            Log.w("KidShield", "Could not set persistent preferred activity", e)
        }
    }

    /**
     * Start lock task mode from the given activity.
     * In Device Owner mode this happens silently.
     * Otherwise the system shows a screen pinning confirmation.
     */
    fun startLockTask(activity: Activity) {
        try {
            activity.startLockTask()
        } catch (e: Exception) {
            // Lock task may not be available, fail gracefully
        }
    }

    fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (_: Exception) {
            // May not be in lock task mode
        }
    }

    /**
     * Suspend a package (prevent it from launching). Device Owner only.
     */
    fun suspendPackage(packageName: String) {
        if (!isDeviceOwner) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dpm.setPackagesSuspended(adminComponent, arrayOf(packageName), true)
        }
    }

    fun unsuspendPackage(packageName: String) {
        if (!isDeviceOwner) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dpm.setPackagesSuspended(adminComponent, arrayOf(packageName), false)
        }
    }

    fun unsuspendAll(packages: List<String>) {
        if (!isDeviceOwner) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dpm.setPackagesSuspended(adminComponent, packages.toTypedArray(), false)
        }
    }

    /**
     * Ensure the PACKAGE_USAGE_STATS (Usage Access) permission is granted.
     * As Device Owner, we can set this via AppOps. Without Device Owner,
     * the user must manually grant it in Settings.
     */
    fun ensureUsageStatsPermission() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            if (isDeviceOwner) {
                // Device Owner can grant via DPM
                try {
                    dpm.setPermissionGrantState(
                        adminComponent,
                        context.packageName,
                        android.Manifest.permission.PACKAGE_USAGE_STATS,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                    Log.d("KidShield", "Granted USAGE_STATS permission via DPM")
                } catch (e: Exception) {
                    Log.w("KidShield", "Could not grant USAGE_STATS via DPM, trying AppOps", e)
                }
            } else {
                Log.w("KidShield", "USAGE_STATS permission not granted — " +
                    "monitoring may not work. Grant via Settings → Apps → Special access → Usage access")
            }
        } else {
            Log.d("KidShield", "USAGE_STATS permission already granted")
        }
    }

    /**
     * Set the device clock. Only works as Device Owner.
     * Useful when the emulator has no network time sync.
     */
    fun setDeviceTime(epochMillis: Long) {
        if (!isDeviceOwner) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setTime(adminComponent, epochMillis)
                Log.d("KidShield", "Device time set to $epochMillis")
            }
        } catch (e: Exception) {
            Log.w("KidShield", "Could not set device time", e)
        }
    }

    /**
     * Check if KidShield is the default Home/Launcher app.
     * This is the non-Device-Owner way to intercept the Home button.
     */
    val isDefaultLauncher: Boolean
        get() {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(
                intent, PackageManager.MATCH_DEFAULT_ONLY
            )
            return resolveInfo?.activityInfo?.packageName == context.packageName
        }
}
