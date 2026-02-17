package com.kidshield.tv.domain.usecase

import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.model.TimeLimitEntity
import com.kidshield.tv.data.repository.UsageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckTimeLimitUseCaseTest {

    private lateinit var usageRepository: UsageRepository
    private lateinit var timeLimitDao: TimeLimitDao
    private lateinit var useCase: CheckTimeLimitUseCase

    @Before
    fun setup() {
        usageRepository = mockk()
        timeLimitDao = mockk()
        useCase = CheckTimeLimitUseCase(usageRepository, timeLimitDao)
    }

    @Test
    fun `returns correct remaining minutes when under limit`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test") } returns TimeLimitEntity(
            packageName = "com.test",
            dailyLimitMinutes = 60
        )
        coEvery { usageRepository.getTodayUsage("com.test") } returns 20

        val status = useCase("com.test")

        assertEquals(60, status.dailyLimitMinutes)
        assertEquals(20, status.minutesUsedToday)
        assertEquals(40, status.minutesRemaining)
        assertFalse(status.isExceeded)
    }

    @Test
    fun `isExceeded is true when usage meets limit exactly`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test") } returns TimeLimitEntity(
            packageName = "com.test",
            dailyLimitMinutes = 60
        )
        coEvery { usageRepository.getTodayUsage("com.test") } returns 60

        val status = useCase("com.test")

        assertEquals(0, status.minutesRemaining)
        assertTrue(status.isExceeded)
    }

    @Test
    fun `isExceeded is true when usage exceeds limit`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test") } returns TimeLimitEntity(
            packageName = "com.test",
            dailyLimitMinutes = 30
        )
        coEvery { usageRepository.getTodayUsage("com.test") } returns 45

        val status = useCase("com.test")

        assertEquals(0, status.minutesRemaining)
        assertTrue(status.isExceeded)
    }

    @Test
    fun `returns null remaining when no time limit is set`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test") } returns null
        coEvery { usageRepository.getTodayUsage("com.test") } returns 100

        val status = useCase("com.test")

        assertNull(status.dailyLimitMinutes)
        assertNull(status.minutesRemaining)
        assertFalse(status.isExceeded)
    }

    @Test
    fun `1 minute limit with 0 usage shows 1 remaining`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test") } returns TimeLimitEntity(
            packageName = "com.test",
            dailyLimitMinutes = 1
        )
        coEvery { usageRepository.getTodayUsage("com.test") } returns 0

        val status = useCase("com.test")

        assertEquals(1, status.minutesRemaining)
        assertFalse(status.isExceeded)
    }

    @Test
    fun `1 minute limit with 1 usage is exceeded`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test") } returns TimeLimitEntity(
            packageName = "com.test",
            dailyLimitMinutes = 1
        )
        coEvery { usageRepository.getTodayUsage("com.test") } returns 1

        val status = useCase("com.test")

        assertEquals(0, status.minutesRemaining)
        assertTrue(status.isExceeded)
    }
}
