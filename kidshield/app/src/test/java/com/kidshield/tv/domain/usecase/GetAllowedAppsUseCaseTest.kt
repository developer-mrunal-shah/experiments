package com.kidshield.tv.domain.usecase

import app.cash.turbine.test
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.data.repository.SettingsRepository
import com.kidshield.tv.domain.model.AgeProfile
import com.kidshield.tv.domain.model.AppCategory
import com.kidshield.tv.domain.model.StreamingApp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetAllowedAppsUseCaseTest {

    private lateinit var appRepository: AppRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: GetAllowedAppsUseCase

    private fun makeApp(
        pkg: String,
        name: String = pkg,
        ageProfile: AgeProfile = AgeProfile.ALL,
        isKidsVariant: Boolean = false
    ) = StreamingApp(
        packageName = pkg,
        displayName = name,
        isInstalled = true,
        isAllowed = true,
        category = AppCategory.STREAMING,
        iconDrawable = null,
        ageProfile = ageProfile,
        isKidsVariant = isKidsVariant,
        dailyMinutesRemaining = null,
        dailyLimitMinutes = null
    )

    @Before
    fun setup() {
        appRepository = mockk()
        settingsRepository = mockk()
        useCase = GetAllowedAppsUseCase(appRepository, settingsRepository)
    }

    @Test
    fun `returns all apps when age profile is ALL`() = runTest {
        val apps = listOf(
            makeApp("com.netflix.ninja", "Netflix"),
            makeApp("com.google.android.youtube.tv", "YouTube"),
        )
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { settingsRepository.getAgeProfile() } returns flowOf(AgeProfile.ALL)

        useCase().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            awaitComplete()
        }
    }

    @Test
    fun `hides regular YouTube for CHILD when YouTube Kids is available`() = runTest {
        val apps = listOf(
            makeApp("com.google.android.youtube.tv", "YouTube"),
            makeApp("com.google.android.youtube.tvkids", "YouTube Kids", isKidsVariant = true),
            makeApp("com.netflix.ninja", "Netflix"),
        )
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { settingsRepository.getAgeProfile() } returns flowOf(AgeProfile.CHILD)

        useCase().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(
                "Regular YouTube should be hidden for CHILD",
                result.none { it.packageName == "com.google.android.youtube.tv" }
            )
            assertTrue(
                "YouTube Kids should still be shown",
                result.any { it.packageName == "com.google.android.youtube.tvkids" }
            )
            awaitComplete()
        }
    }

    @Test
    fun `shows regular YouTube for CHILD when YouTube Kids is NOT available`() = runTest {
        val apps = listOf(
            makeApp("com.google.android.youtube.tv", "YouTube"),
            makeApp("com.netflix.ninja", "Netflix"),
        )
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { settingsRepository.getAgeProfile() } returns flowOf(AgeProfile.CHILD)

        useCase().test {
            val result = awaitItem()
            assertTrue(
                "Regular YouTube should show when Kids version is not available",
                result.any { it.packageName == "com.google.android.youtube.tv" }
            )
            awaitComplete()
        }
    }

    @Test
    fun `shows regular YouTube for TEEN even when YouTube Kids is available`() = runTest {
        val apps = listOf(
            makeApp("com.google.android.youtube.tv", "YouTube"),
            makeApp("com.google.android.youtube.tvkids", "YouTube Kids", isKidsVariant = true),
        )
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { settingsRepository.getAgeProfile() } returns flowOf(AgeProfile.TEEN)

        useCase().test {
            val result = awaitItem()
            assertTrue(
                "Regular YouTube should show for TEEN",
                result.any { it.packageName == "com.google.android.youtube.tv" }
            )
            awaitComplete()
        }
    }

    @Test
    fun `hides regular YouTube for TODDLER when YouTube Kids is available`() = runTest {
        val apps = listOf(
            makeApp("com.google.android.youtube.tv", "YouTube"),
            makeApp("com.google.android.youtube.tvkids", "YouTube Kids", isKidsVariant = true),
        )
        every { appRepository.getAllowedApps() } returns flowOf(apps)
        every { settingsRepository.getAgeProfile() } returns flowOf(AgeProfile.TODDLER)

        useCase().test {
            val result = awaitItem()
            assertTrue(
                "Regular YouTube should be hidden for TODDLER",
                result.none { it.packageName == "com.google.android.youtube.tv" }
            )
            awaitComplete()
        }
    }

    @Test
    fun `returns empty list when no apps are allowed`() = runTest {
        every { appRepository.getAllowedApps() } returns flowOf(emptyList())
        every { settingsRepository.getAgeProfile() } returns flowOf(AgeProfile.ALL)

        useCase().test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            awaitComplete()
        }
    }
}
