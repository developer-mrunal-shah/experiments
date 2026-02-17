package com.kidshield.tv.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.model.TimeLimitEntity
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.data.repository.UsageRepository
import com.kidshield.tv.domain.model.AgeProfile
import com.kidshield.tv.domain.model.AppCategory
import com.kidshield.tv.domain.model.StreamingApp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchAppUseCaseTest {

    private lateinit var appRepository: AppRepository
    private lateinit var usageRepository: UsageRepository
    private lateinit var timeLimitDao: TimeLimitDao
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var useCase: LaunchAppUseCase

    private fun makeApp(
        pkg: String = "com.test.app",
        name: String = "Test App",
        allowed: Boolean = true,
        installed: Boolean = true
    ) = StreamingApp(
        packageName = pkg,
        displayName = name,
        isInstalled = installed,
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
        usageRepository = mockk()
        timeLimitDao = mockk()
        context = mockk()
        packageManager = mockk()

        every { context.packageManager } returns packageManager

        useCase = LaunchAppUseCase(appRepository, usageRepository, timeLimitDao, context)
    }

    // ── App not in database → AppNotInstalled ─────────────────────

    @Test
    fun `returns AppNotInstalled when app is not in repository`() = runTest {
        coEvery { appRepository.getApp("com.unknown.app") } returns null

        val result = useCase("com.unknown.app")

        assertTrue(result is LaunchAppUseCase.LaunchResult.AppNotInstalled)
        assertEquals("com.unknown.app", (result as LaunchAppUseCase.LaunchResult.AppNotInstalled).packageName)
    }

    // ── App not allowed → NotAllowed ──────────────────────────────

    @Test
    fun `returns NotAllowed when app is not in the allowlist`() = runTest {
        coEvery { appRepository.getApp("com.blocked.app") } returns makeApp(
            pkg = "com.blocked.app", allowed = false
        )

        val result = useCase("com.blocked.app")

        assertTrue(result is LaunchAppUseCase.LaunchResult.NotAllowed)
    }

    // ── Time schedule enforcement ─────────────────────────────────

    @Test
    fun `returns OutsideSchedule when current time is before allowed start`() = runTest {
        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            allowedStartTime = "23:00",
            allowedEndTime = "23:59",
            allowedDaysOfWeek = "1,2,3,4,5,6,7"
        )
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 0

        val result = useCase("com.test.app")

        // Unless it's between 23:00-23:59, this should return OutsideSchedule
        // We can't control LocalTime.now() without a clock abstraction,
        // so this test verifies the mechanism works if outside window
        if (java.time.LocalTime.now().hour != 23) {
            assertTrue(
                "Expected OutsideSchedule but got $result",
                result is LaunchAppUseCase.LaunchResult.OutsideSchedule
            )
        }
    }

    @Test
    fun `returns NotAllowed when today is not an allowed day`() = runTest {
        // Set allowed days to a day that's NOT today
        val today = java.time.LocalDate.now().dayOfWeek.value
        val notToday = if (today == 1) "2" else "1"

        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            allowedDaysOfWeek = notToday
        )
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 0

        val result = useCase("com.test.app")

        assertTrue(
            "Expected NotAllowed for wrong day but got $result",
            result is LaunchAppUseCase.LaunchResult.NotAllowed
        )
    }

    // ── Daily time limit enforcement ──────────────────────────────

    @Test
    fun `returns TimeLimitReached when daily usage exceeds limit`() = runTest {
        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            dailyLimitMinutes = 60
        )
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 60

        val result = useCase("com.test.app")

        assertTrue(result is LaunchAppUseCase.LaunchResult.TimeLimitReached)
    }

    @Test
    fun `returns TimeLimitReached when usage exceeds 1 minute limit`() = runTest {
        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            dailyLimitMinutes = 1
        )
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 1

        val result = useCase("com.test.app")

        assertTrue(
            "1 min limit with 1 min usage should be TimeLimitReached, got $result",
            result is LaunchAppUseCase.LaunchResult.TimeLimitReached
        )
    }

    @Test
    fun `allows launch when usage is under limit`() = runTest {
        val launchIntent = mockk<Intent>(relaxed = true)

        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            dailyLimitMinutes = 60
        )
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 30
        every { packageManager.getLeanbackLaunchIntentForPackage("com.test.app") } returns launchIntent
        every { context.startActivity(any()) } returns Unit

        val result = useCase("com.test.app")

        assertTrue(
            "Usage under limit should succeed, got $result",
            result is LaunchAppUseCase.LaunchResult.Success
        )
    }

    // ── Launch intent resolution ──────────────────────────────────

    @Test
    fun `uses leanback launch intent when available`() = runTest {
        val leanbackIntent = mockk<Intent>(relaxed = true)

        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns null
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 0
        every { packageManager.getLeanbackLaunchIntentForPackage("com.test.app") } returns leanbackIntent
        every { context.startActivity(any()) } returns Unit

        val result = useCase("com.test.app")

        assertTrue(result is LaunchAppUseCase.LaunchResult.Success)
        verify { packageManager.getLeanbackLaunchIntentForPackage("com.test.app") }
    }

    @Test
    fun `falls back to standard launch intent when leanback is null`() = runTest {
        val standardIntent = mockk<Intent>(relaxed = true)

        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns null
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 0
        every { packageManager.getLeanbackLaunchIntentForPackage("com.test.app") } returns null
        every { packageManager.getLaunchIntentForPackage("com.test.app") } returns standardIntent
        every { context.startActivity(any()) } returns Unit

        val result = useCase("com.test.app")

        assertTrue(result is LaunchAppUseCase.LaunchResult.Success)
        verify { packageManager.getLaunchIntentForPackage("com.test.app") }
    }

    @Test
    fun `returns AppNotInstalled when both launch intents are null`() = runTest {
        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns null
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 0
        every { packageManager.getLeanbackLaunchIntentForPackage("com.test.app") } returns null
        every { packageManager.getLaunchIntentForPackage("com.test.app") } returns null

        val result = useCase("com.test.app")

        assertTrue(
            "Should return AppNotInstalled when no launch intent found, got $result",
            result is LaunchAppUseCase.LaunchResult.AppNotInstalled
        )
    }

    // ── No time limit set → unlimited ─────────────────────────────

    @Test
    fun `allows launch when no time limit entity exists`() = runTest {
        val launchIntent = mockk<Intent>(relaxed = true)

        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns null
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 999
        every { packageManager.getLeanbackLaunchIntentForPackage("com.test.app") } returns launchIntent
        every { context.startActivity(any()) } returns Unit

        val result = useCase("com.test.app")

        assertTrue(
            "No time limit should mean unlimited usage, got $result",
            result is LaunchAppUseCase.LaunchResult.Success
        )
    }

    // ── All days allowed works ────────────────────────────────────

    @Test
    fun `allows launch when all days are allowed`() = runTest {
        val launchIntent = mockk<Intent>(relaxed = true)

        coEvery { appRepository.getApp("com.test.app") } returns makeApp()
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            allowedDaysOfWeek = "1,2,3,4,5,6,7"
        )
        coEvery { usageRepository.getTodayUsage("com.test.app") } returns 0
        every { packageManager.getLeanbackLaunchIntentForPackage("com.test.app") } returns launchIntent
        every { context.startActivity(any()) } returns Unit

        val result = useCase("com.test.app")

        assertTrue(result is LaunchAppUseCase.LaunchResult.Success)
    }
}
