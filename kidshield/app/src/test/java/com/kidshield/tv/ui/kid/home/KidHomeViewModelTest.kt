package com.kidshield.tv.ui.kid.home

import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.model.TimeLimitEntity
import com.kidshield.tv.data.model.UsageLogEntity
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.data.repository.UsageRepository
import com.kidshield.tv.domain.model.AgeProfile
import com.kidshield.tv.domain.model.AppCategory
import com.kidshield.tv.domain.model.StreamingApp
import com.kidshield.tv.domain.usecase.CheckTimeLimitUseCase
import com.kidshield.tv.domain.usecase.GetAllowedAppsUseCase
import com.kidshield.tv.domain.usecase.LaunchAppUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KidHomeViewModelTest {

    private lateinit var getAllowedAppsUseCase: GetAllowedAppsUseCase
    private lateinit var launchAppUseCase: LaunchAppUseCase
    private lateinit var checkTimeLimitUseCase: CheckTimeLimitUseCase
    private lateinit var usageRepository: UsageRepository
    private lateinit var appRepository: AppRepository
    private lateinit var timeLimitDao: TimeLimitDao

    private val testDispatcher = StandardTestDispatcher()

    private fun makeApp(
        pkg: String = "com.test.app",
        name: String = "Test App",
        category: AppCategory = AppCategory.STREAMING
    ) = StreamingApp(
        packageName = pkg,
        displayName = name,
        isInstalled = true,
        isAllowed = true,
        category = category,
        iconDrawable = null,
        ageProfile = AgeProfile.ALL,
        isKidsVariant = false,
        dailyMinutesRemaining = null,
        dailyLimitMinutes = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getAllowedAppsUseCase = mockk()
        launchAppUseCase = mockk(relaxed = true)
        checkTimeLimitUseCase = mockk()
        usageRepository = mockk()
        appRepository = mockk(relaxed = true)
        timeLimitDao = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): KidHomeViewModel {
        return KidHomeViewModel(
            getAllowedAppsUseCase,
            launchAppUseCase,
            checkTimeLimitUseCase,
            usageRepository,
            appRepository,
            timeLimitDao
        )
    }

    // ── Greeting ────────────────────────────────────────────────────

    @Test
    fun `greeting is set on initialization`() = runTest {
        every { getAllowedAppsUseCase() } returns flowOf(emptyList())
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        val greeting = viewModel.uiState.value.greeting
        assertTrue(
            "Greeting should be one of morning/afternoon/evening, got: '$greeting'",
            greeting in listOf("Good morning!", "Good afternoon!", "Good evening!")
        )
    }

    // ── App loading and categorization ──────────────────────────────

    @Test
    fun `loads and categorizes apps correctly`() = runTest {
        val apps = listOf(
            makeApp("com.netflix.ninja", "Netflix", AppCategory.STREAMING),
            makeApp("com.khan.academy", "Khan Academy", AppCategory.EDUCATION)
        )

        every { getAllowedAppsUseCase() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        coEvery { checkTimeLimitUseCase("com.netflix.ninja") } returns CheckTimeLimitUseCase.TimeLimitStatus(
            packageName = "com.netflix.ninja",
            dailyLimitMinutes = 60,
            minutesUsedToday = 10,
            minutesRemaining = 50,
            isExceeded = false
        )
        coEvery { checkTimeLimitUseCase("com.khan.academy") } returns CheckTimeLimitUseCase.TimeLimitStatus(
            packageName = "com.khan.academy",
            dailyLimitMinutes = null,
            minutesUsedToday = 0,
            minutesRemaining = null,
            isExceeded = false
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.categories.size)
        assertTrue(state.categories.containsKey("Streaming"))
        assertTrue(state.categories.containsKey("Education"))
        assertEquals(1, state.categories["Streaming"]!!.size)
        assertEquals(1, state.categories["Education"]!!.size)
    }

    @Test
    fun `enriches apps with remaining time from CheckTimeLimitUseCase`() = runTest {
        val apps = listOf(makeApp("com.netflix.ninja", "Netflix"))

        every { getAllowedAppsUseCase() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        coEvery { checkTimeLimitUseCase("com.netflix.ninja") } returns CheckTimeLimitUseCase.TimeLimitStatus(
            packageName = "com.netflix.ninja",
            dailyLimitMinutes = 60,
            minutesUsedToday = 45,
            minutesRemaining = 15,
            isExceeded = false
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val netflix = viewModel.uiState.value.categories["Streaming"]!!.first()
        assertEquals(15, netflix.dailyMinutesRemaining)
        assertEquals(60, netflix.dailyLimitMinutes)
    }

    // ── Stale time display bug fix ──────────────────────────────────

    @Test
    fun `refreshes remaining time when usage flow emits new data`() = runTest {
        val apps = listOf(makeApp("com.netflix.ninja", "Netflix"))
        val usageFlow = MutableStateFlow<List<UsageLogEntity>>(emptyList())

        every { getAllowedAppsUseCase() } returns flowOf(apps)
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns usageFlow

        // Initially: 50 minutes remaining
        coEvery { checkTimeLimitUseCase("com.netflix.ninja") } returns CheckTimeLimitUseCase.TimeLimitStatus(
            packageName = "com.netflix.ninja",
            dailyLimitMinutes = 60,
            minutesUsedToday = 10,
            minutesRemaining = 50,
            isExceeded = false
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val initialRemaining = viewModel.uiState.value.categories["Streaming"]!!.first().dailyMinutesRemaining
        assertEquals(50, initialRemaining)

        // Now the monitor records usage → CheckTimeLimitUseCase returns updated data
        coEvery { checkTimeLimitUseCase("com.netflix.ninja") } returns CheckTimeLimitUseCase.TimeLimitStatus(
            packageName = "com.netflix.ninja",
            dailyLimitMinutes = 60,
            minutesUsedToday = 11,
            minutesRemaining = 49,
            isExceeded = false
        )

        // Emit new usage data to trigger combine re-evaluation
        usageFlow.value = listOf(
            UsageLogEntity(
                packageName = "com.netflix.ninja",
                date = "2025-01-01",
                totalMinutesUsed = 11
            )
        )
        advanceUntilIdle()

        val updatedRemaining = viewModel.uiState.value.categories["Streaming"]!!.first().dailyMinutesRemaining
        assertEquals(
            "Remaining time should update when usage flow emits (was 50, now should be 49)",
            49, updatedRemaining
        )
    }

    // ── Empty state ─────────────────────────────────────────────────

    @Test
    fun `shows empty categories when no apps are allowed`() = runTest {
        every { getAllowedAppsUseCase() } returns flowOf(emptyList())
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.categories.isEmpty())
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    // ── Launch app result handling ──────────────────────────────────

    @Test
    fun `launchApp sets timesUpApp when time limit reached`() = runTest {
        every { getAllowedAppsUseCase() } returns flowOf(emptyList())
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        coEvery { launchAppUseCase("com.netflix.ninja") } returns
            LaunchAppUseCase.LaunchResult.TimeLimitReached("Netflix")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchApp("com.netflix.ninja")
        advanceUntilIdle()

        val timesUp = viewModel.uiState.value.timesUpApp
        assertNotNull("timesUpApp should be set when time limit reached", timesUp)
        assertEquals("com.netflix.ninja", timesUp!!.first)
        assertEquals("Netflix", timesUp.second)
    }

    @Test
    fun `launchApp sets error for OutsideSchedule`() = runTest {
        every { getAllowedAppsUseCase() } returns flowOf(emptyList())
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        coEvery { launchAppUseCase("com.test.app") } returns
            LaunchAppUseCase.LaunchResult.OutsideSchedule("09:00", "21:00")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchApp("com.test.app")
        advanceUntilIdle()

        assertEquals("Available 09:00 - 21:00", viewModel.uiState.value.launchError)
    }

    @Test
    fun `clearTimesUp resets timesUpApp`() = runTest {
        every { getAllowedAppsUseCase() } returns flowOf(emptyList())
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        coEvery { launchAppUseCase("com.test.app") } returns
            LaunchAppUseCase.LaunchResult.TimeLimitReached("Test")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchApp("com.test.app")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.timesUpApp)

        viewModel.clearTimesUp()
        assertEquals(null, viewModel.uiState.value.timesUpApp)
    }

    @Test
    fun `clearError resets launchError`() = runTest {
        every { getAllowedAppsUseCase() } returns flowOf(emptyList())
        every { timeLimitDao.getAllTimeLimits() } returns flowOf(emptyList())
        every { usageRepository.getTodayAllUsage() } returns flowOf(emptyList())

        coEvery { launchAppUseCase("com.test.app") } returns
            LaunchAppUseCase.LaunchResult.NotAllowed("blocked")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchApp("com.test.app")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.launchError)

        viewModel.clearError()
        assertEquals(null, viewModel.uiState.value.launchError)
    }
}
