package com.kidshield.tv.service

import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.model.TimeLimitEntity
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.data.repository.UsageRepository
import com.kidshield.tv.domain.model.AgeProfile
import com.kidshield.tv.domain.model.AppCategory
import com.kidshield.tv.domain.model.StreamingApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppMonitorLogicTest {

    private lateinit var appRepository: AppRepository
    private lateinit var usageRepository: UsageRepository
    private lateinit var timeLimitDao: TimeLimitDao
    private lateinit var logic: AppMonitorLogic

    private val ownPackage = "com.kidshield.tv"
    private val pollIntervalMs = 10_000L // 10 seconds

    private fun makeApp(pkg: String, name: String, allowed: Boolean = true) = StreamingApp(
        packageName = pkg,
        displayName = name,
        isInstalled = true,
        isAllowed = allowed,
        category = AppCategory.STREAMING,
        iconDrawable = null,
        ageProfile = AgeProfile.ALL,
        isKidsVariant = false,
        dailyMinutesRemaining = null,
        dailyLimitMinutes = null
    )

    @Before
    fun setup() {
        appRepository = mockk()
        usageRepository = mockk(relaxed = true)
        timeLimitDao = mockk()
        logic = AppMonitorLogic(appRepository, usageRepository, timeLimitDao)
    }

    // ── Ignore cases ──

    @Test
    fun `ignores own package`() = runTest {
        val action = logic.evaluate(ownPackage, ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.Ignore)
    }

    @Test
    fun `ignores system UI`() = runTest {
        val action = logic.evaluate("com.android.systemui", ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.Ignore)
    }

    @Test
    fun `ignores TV launcher`() = runTest {
        val action = logic.evaluate("com.google.android.tvlauncher", ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.Ignore)
    }

    @Test
    fun `ignores settings`() = runTest {
        val action = logic.evaluate("com.android.settings", ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.Ignore)
    }

    @Test
    fun `ignores launcher`() = runTest {
        val action = logic.evaluate("com.android.launcher", ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.Ignore)
    }

    // ── Block not-allowed cases ──

    @Test
    fun `blocks unknown app not in repository`() = runTest {
        coEvery { appRepository.getApp("com.unknown.app") } returns null

        val action = logic.evaluate("com.unknown.app", ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.BlockNotAllowed)
        assertEquals("com.unknown.app", (action as AppMonitorLogic.MonitorAction.BlockNotAllowed).packageName)
    }

    @Test
    fun `blocks app that is not allowed`() = runTest {
        coEvery { appRepository.getApp("com.blocked.app") } returns
            makeApp("com.blocked.app", "Blocked App", allowed = false)

        val action = logic.evaluate("com.blocked.app", ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.BlockNotAllowed)
    }

    // ── Time limit enforcement cases ──

    @Test
    fun `enforces time limit when usage equals limit of 1 minute`() = runTest {
        val pkg = "com.apple.atve.androidtv.appletv"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Apple TV+")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 1
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 1)

        val action = logic.evaluate(pkg, ownPackage, pollIntervalMs)

        assertTrue(action is AppMonitorLogic.MonitorAction.EnforceTimeLimit)
        val enforce = action as AppMonitorLogic.MonitorAction.EnforceTimeLimit
        assertEquals(pkg, enforce.packageName)
        assertEquals("Apple TV+", enforce.appName)
        assertEquals(1, enforce.minutesUsed)
        assertEquals(1, enforce.dailyLimit)
    }

    @Test
    fun `enforces time limit when usage exceeds limit`() = runTest {
        val pkg = "com.netflix.ninja"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Netflix")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 35
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 30)

        val action = logic.evaluate(pkg, ownPackage, pollIntervalMs)

        assertTrue(action is AppMonitorLogic.MonitorAction.EnforceTimeLimit)
        val enforce = action as AppMonitorLogic.MonitorAction.EnforceTimeLimit
        assertEquals(35, enforce.minutesUsed)
        assertEquals(30, enforce.dailyLimit)
    }

    // ── Record usage cases ──

    @Test
    fun `records usage when under limit`() = runTest {
        val pkg = "com.netflix.ninja"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Netflix")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 10
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 60)

        val action = logic.evaluate(pkg, ownPackage, pollIntervalMs)

        assertTrue(action is AppMonitorLogic.MonitorAction.RecordUsage)
        val record = action as AppMonitorLogic.MonitorAction.RecordUsage
        assertEquals("Netflix", record.appName)
        assertEquals(1, record.incrementMinutes) // 10_000ms / 60_000 = 0, coerced to 1
        assertEquals(11, record.newTotalMinutes)

        coVerify { usageRepository.recordUsage(pkg, 1) }
    }

    @Test
    fun `records usage when no time limit is set (unlimited)`() = runTest {
        val pkg = "com.disney.disneyplus"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Disney+")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 500
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns null

        val action = logic.evaluate(pkg, ownPackage, pollIntervalMs)

        assertTrue(action is AppMonitorLogic.MonitorAction.RecordUsage)
        coVerify { usageRepository.recordUsage(pkg, 1) }
    }

    @Test
    fun `records usage at zero with 1-min limit`() = runTest {
        val pkg = "com.apple.atve.androidtv.appletv"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Apple TV+")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 0
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 1)

        val action = logic.evaluate(pkg, ownPackage, pollIntervalMs)

        // First poll: usage=0, limit=1 → 0 < 1 → record usage
        assertTrue(action is AppMonitorLogic.MonitorAction.RecordUsage)
        val record = action as AppMonitorLogic.MonitorAction.RecordUsage
        assertEquals(1, record.incrementMinutes)
        assertEquals(1, record.newTotalMinutes)

        coVerify { usageRepository.recordUsage(pkg, 1) }
    }

    // ── Full 1-minute enforcement flow simulation ──

    @Test
    fun `full flow - 1-min limit - first poll records, second poll enforces`() = runTest {
        val pkg = "com.apple.atve.androidtv.appletv"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Apple TV+")
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 1)

        // ── First poll cycle (t=10s): usage is 0, limit is 1 ──
        coEvery { usageRepository.getTodayUsage(pkg) } returns 0

        val action1 = logic.evaluate(pkg, ownPackage, pollIntervalMs)
        assertTrue("First poll should record usage", action1 is AppMonitorLogic.MonitorAction.RecordUsage)
        coVerify { usageRepository.recordUsage(pkg, 1) }

        // ── Second poll cycle (t=20s): usage is now 1, limit is 1 ──
        coEvery { usageRepository.getTodayUsage(pkg) } returns 1

        val action2 = logic.evaluate(pkg, ownPackage, pollIntervalMs)
        assertTrue("Second poll should enforce time limit",
            action2 is AppMonitorLogic.MonitorAction.EnforceTimeLimit)

        val enforce = action2 as AppMonitorLogic.MonitorAction.EnforceTimeLimit
        assertEquals(pkg, enforce.packageName)
        assertEquals("Apple TV+", enforce.appName)
        assertEquals(1, enforce.minutesUsed)
        assertEquals(1, enforce.dailyLimit)
    }

    @Test
    fun `full flow - 30-min limit - records for many polls then enforces`() = runTest {
        val pkg = "com.netflix.ninja"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Netflix")
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 30)

        // Simulate usage growing over time
        for (minute in 0 until 30) {
            coEvery { usageRepository.getTodayUsage(pkg) } returns minute
            val action = logic.evaluate(pkg, ownPackage, pollIntervalMs)
            assertTrue("Should record usage at minute $minute",
                action is AppMonitorLogic.MonitorAction.RecordUsage)
        }

        // At minute 30: usage equals limit → enforce
        coEvery { usageRepository.getTodayUsage(pkg) } returns 30
        val enforceAction = logic.evaluate(pkg, ownPackage, pollIntervalMs)
        assertTrue("Should enforce at minute 30",
            enforceAction is AppMonitorLogic.MonitorAction.EnforceTimeLimit)
    }

    @Test
    fun `does not record usage when enforcing (no side effect)`() = runTest {
        val pkg = "com.test.app"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Test App")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 60
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 60)

        val action = logic.evaluate(pkg, ownPackage, pollIntervalMs)
        assertTrue(action is AppMonitorLogic.MonitorAction.EnforceTimeLimit)

        // recordUsage should NOT have been called during enforcement
        coVerify(exactly = 0) { usageRepository.recordUsage(any(), any()) }
    }

    @Test
    fun `increment is at least 1 minute even with short poll interval`() = runTest {
        val pkg = "com.test.app"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Test App")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 0
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 60)

        // Even with a 1-second poll interval, increment is coerced to 1 minute
        val action = logic.evaluate(pkg, ownPackage, 1_000L)
        assertTrue(action is AppMonitorLogic.MonitorAction.RecordUsage)
        assertEquals(1, (action as AppMonitorLogic.MonitorAction.RecordUsage).incrementMinutes)
    }

    @Test
    fun `increment scales with longer poll interval`() = runTest {
        val pkg = "com.test.app"
        coEvery { appRepository.getApp(pkg) } returns makeApp(pkg, "Test App")
        coEvery { usageRepository.getTodayUsage(pkg) } returns 0
        coEvery { timeLimitDao.getTimeLimitForAppOnce(pkg) } returns
            TimeLimitEntity(packageName = pkg, dailyLimitMinutes = 60)

        // With a 5-minute poll interval, increment should be 5
        val action = logic.evaluate(pkg, ownPackage, 300_000L)
        assertTrue(action is AppMonitorLogic.MonitorAction.RecordUsage)
        assertEquals(5, (action as AppMonitorLogic.MonitorAction.RecordUsage).incrementMinutes)
    }
}
