package com.kidshield.tv.ui.parent.timelimits

import app.cash.turbine.test
import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.model.TimeLimitEntity
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.domain.model.AgeProfile
import com.kidshield.tv.domain.model.AppCategory
import com.kidshield.tv.domain.model.StreamingApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimeLimitsViewModelTest {

    private lateinit var appRepository: AppRepository
    private lateinit var timeLimitDao: TimeLimitDao
    private val testDispatcher = StandardTestDispatcher()

    private fun makeApp(pkg: String, name: String) = StreamingApp(
        packageName = pkg,
        displayName = name,
        isInstalled = true,
        isAllowed = true,
        category = AppCategory.STREAMING,
        iconDrawable = null,
        ageProfile = AgeProfile.ALL,
        isKidsVariant = false,
        dailyMinutesRemaining = null,
        dailyLimitMinutes = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appRepository = mockk()
        timeLimitDao = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TimeLimitsViewModel {
        return TimeLimitsViewModel(appRepository, timeLimitDao)
    }

    @Test
    fun `loads apps with their time limits`() = runTest {
        val apps = listOf(
            makeApp("com.netflix.ninja", "Netflix"),
            makeApp("com.disney.disneyplus", "Disney+"),
        )
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(
            listOf(
                TimeLimitEntity(packageName = "com.netflix.ninja", dailyLimitMinutes = 30),
                TimeLimitEntity(packageName = "com.disney.disneyplus", dailyLimitMinutes = 120),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.apps.size)
        assertEquals(30, state.apps.find { it.packageName == "com.netflix.ninja" }!!.dailyLimitMinutes)
        assertEquals(120, state.apps.find { it.packageName == "com.disney.disneyplus" }!!.dailyLimitMinutes)
    }

    @Test
    fun `defaults to 60 minutes when no time limit entity exists`() = runTest {
        val apps = listOf(makeApp("com.test.app", "Test"))
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(60, state.apps[0].dailyLimitMinutes)
    }

    @Test
    fun `updateDailyLimit persists new limit to database`() = runTest {
        val apps = listOf(makeApp("com.test.app", "Test"))
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(
            listOf(TimeLimitEntity(packageName = "com.test.app", dailyLimitMinutes = 60))
        )
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            dailyLimitMinutes = 60
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDailyLimit("com.test.app", 1)
        advanceUntilIdle()

        coVerify {
            timeLimitDao.upsertTimeLimit(
                match { it.packageName == "com.test.app" && it.dailyLimitMinutes == 1 }
            )
        }
    }

    @Test
    fun `setAllToMinutes updates all apps`() = runTest {
        val apps = listOf(
            makeApp("com.app.one", "One"),
            makeApp("com.app.two", "Two"),
        )
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(
            listOf(
                TimeLimitEntity(packageName = "com.app.one", dailyLimitMinutes = 60),
                TimeLimitEntity(packageName = "com.app.two", dailyLimitMinutes = 120),
            )
        )
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.app.one") } returns TimeLimitEntity(
            packageName = "com.app.one", dailyLimitMinutes = 60
        )
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.app.two") } returns TimeLimitEntity(
            packageName = "com.app.two", dailyLimitMinutes = 120
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAllToMinutes(1)
        advanceUntilIdle()

        coVerify {
            timeLimitDao.upsertTimeLimit(
                match { it.packageName == "com.app.one" && it.dailyLimitMinutes == 1 }
            )
        }
        coVerify {
            timeLimitDao.upsertTimeLimit(
                match { it.packageName == "com.app.two" && it.dailyLimitMinutes == 1 }
            )
        }
    }

    @Test
    fun `updateDailyLimit creates new entity if none exists`() = runTest {
        val apps = listOf(makeApp("com.new.app", "New"))
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.new.app") } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDailyLimit("com.new.app", 5)
        advanceUntilIdle()

        coVerify {
            timeLimitDao.upsertTimeLimit(
                match { it.packageName == "com.new.app" && it.dailyLimitMinutes == 5 }
            )
        }
    }

    @Test
    fun `updateSchedule persists time window`() = runTest {
        val apps = listOf(makeApp("com.test.app", "Test"))
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(
            listOf(TimeLimitEntity(packageName = "com.test.app"))
        )
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app"
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSchedule("com.test.app", "09:00", "21:00")
        advanceUntilIdle()

        coVerify {
            timeLimitDao.upsertTimeLimit(
                match {
                    it.packageName == "com.test.app" &&
                    it.allowedStartTime == "09:00" &&
                    it.allowedEndTime == "21:00"
                }
            )
        }
    }
}
