package com.kidshield.tv.data.repository

import android.content.pm.PackageManager
import com.kidshield.tv.data.local.db.dao.AppConfigDao
import com.kidshield.tv.data.local.db.dao.TimeLimitDao
import com.kidshield.tv.data.model.TimeLimitEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppRepositoryImplTest {

    private lateinit var appConfigDao: AppConfigDao
    private lateinit var timeLimitDao: TimeLimitDao
    private lateinit var packageManager: PackageManager
    private lateinit var repository: AppRepositoryImpl

    @Before
    fun setup() {
        appConfigDao = mockk(relaxed = true)
        timeLimitDao = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        repository = AppRepositoryImpl(appConfigDao, timeLimitDao, packageManager)
    }

    // ── setAppAllowed creates default time limit ──────────────────

    @Test
    fun `setAppAllowed creates default time limit when allowing app`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns null

        repository.setAppAllowed("com.test.app", true)

        coVerify { appConfigDao.setAllowed("com.test.app", true) }
        coVerify {
            timeLimitDao.upsertTimeLimit(
                match { it.packageName == "com.test.app" && it.dailyLimitMinutes == 60 }
            )
        }
    }

    @Test
    fun `setAppAllowed does not overwrite existing time limit`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.test.app") } returns TimeLimitEntity(
            packageName = "com.test.app",
            dailyLimitMinutes = 30
        )

        repository.setAppAllowed("com.test.app", true)

        coVerify { appConfigDao.setAllowed("com.test.app", true) }
        coVerify(exactly = 0) { timeLimitDao.upsertTimeLimit(any()) }
    }

    @Test
    fun `setAppAllowed does not create time limit when disabling app`() = runTest {
        repository.setAppAllowed("com.test.app", false)

        coVerify { appConfigDao.setAllowed("com.test.app", false) }
        coVerify(exactly = 0) { timeLimitDao.getTimeLimitForAppOnce(any()) }
        coVerify(exactly = 0) { timeLimitDao.upsertTimeLimit(any()) }
    }

    // ── Default time limit is 60 minutes ──────────────────────────

    @Test
    fun `default time limit for new allowed app is 60 minutes`() = runTest {
        coEvery { timeLimitDao.getTimeLimitForAppOnce("com.new.app") } returns null

        repository.setAppAllowed("com.new.app", true)

        coVerify {
            timeLimitDao.upsertTimeLimit(
                match { it.dailyLimitMinutes == 60 }
            )
        }
    }
}
