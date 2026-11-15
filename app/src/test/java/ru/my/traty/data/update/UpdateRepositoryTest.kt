package ru.my.traty.data.update

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.data.AppSettings
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class UpdateRepositoryTest {
    private val settings get() = ApplicationProvider.getApplicationContext<TratyApp>().container.settings

    private var calls = 0
    private var clock = 1_000_000_000L

    private fun json(tag: String) =
        """{"tag_name":"$tag","draft":false,"prerelease":false,"html_url":"https://github.com/Benbok/traty/releases/tag/$tag","assets":[]}"""

    private fun repo(installed: String = "0.1", body: () -> String) =
        UpdateRepository(settings, installed, fetch = { calls++; body() }, now = { clock })

    // Хранилище настроек общее для тестов в одном процессе: перед каждым тестом и после него возвращаем как было
    private suspend fun reset() {
        settings.saveUpdateCheck(0, "", "")
        settings.dismissUpdate("")
        settings.setUpdateCheckEnabled(true)
    }

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking { reset() }

    @After
    fun tearDown() = kotlinx.coroutines.runBlocking { reset() }

    @Test
    fun newerReleaseIsRememberedAndShownAsBanner() = runTest {
        val result = repo { json("v0.1.1") }.checkNow()

        assertEquals(UpdateCheckResult.Available("0.1.1"), result)
        val s = settings.current()
        assertEquals("0.1.1", s.updateVersion)
        assertEquals(clock, s.updateCheckedAtMillis)
        assertEquals("0.1.1", AvailableUpdate.from(s, "0.1")?.version)
    }

    @Test
    fun sameVersionClearsEarlierFinding() = runTest {
        settings.saveUpdateCheck(1, "0.1.1", "https://github.com/Benbok/traty/releases/latest")

        val result = repo(installed = "0.1.1") { json("v0.1.1") }.checkNow()

        assertEquals(UpdateCheckResult.UpToDate, result)
        assertEquals("", settings.current().updateVersion)
        assertNull(AvailableUpdate.from(settings.current(), "0.1.1"))
    }

    @Test
    fun networkErrorGivesFriendlyMessageAndKeepsState() = runTest {
        settings.saveUpdateCheck(5, "0.1.1", "")

        val result = repo { throw IOException("boom") }.checkNow()

        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals("0.1.1", settings.current().updateVersion)
        assertEquals(5L, settings.current().updateCheckedAtMillis)
    }

    @Test
    fun noReleasesYetIsExplained() = runTest {
        val result = repo { throw NoReleasesException() }.checkNow()
        assertEquals(UpdateCheckResult.Failed("Релизов пока нет"), result)
    }

    @Test
    fun unexpectedAnswerIsAFailure() = runTest {
        val result = repo { """{"message":"Not Found"}""" }.checkNow()
        assertTrue(result is UpdateCheckResult.Failed)
    }

    @Test
    fun silentCheckRunsAtMostOncePerInterval() = runTest {
        val r = repo { json("v0.1") }

        r.checkIfDue()
        assertEquals(1, calls)

        clock += UpdateRepository.CHECK_INTERVAL_MS - 1
        r.checkIfDue()
        assertEquals("ещё рано: сеть не трогаем", 1, calls)

        clock += 2
        r.checkIfDue()
        assertEquals(2, calls)
    }

    @Test
    fun silentCheckDoesNothingWhenDisabled() = runTest {
        settings.setUpdateCheckEnabled(false)

        repo { json("v9.9") }.checkIfDue()

        assertEquals(0, calls)
        assertNull(AvailableUpdate.from(settings.current(), "0.1"))
    }

    @Test
    fun silentCheckSwallowsErrors() = runTest {
        repo { throw IOException("offline") }.checkIfDue()
        assertEquals(1, calls)
    }

    @Test
    fun dismissedVersionHidesBannerButNextOneShows() = runTest {
        repo { json("v0.1.1") }.checkNow()
        settings.dismissUpdate("0.1.1")
        assertNull(AvailableUpdate.from(settings.current(), "0.1"))

        repo { json("v0.2") }.checkNow()
        assertEquals("0.2", AvailableUpdate.from(settings.current(), "0.1")?.version)
    }

    @Test
    fun bannerDisappearsOnceUpdateIsInstalled() {
        val s = AppSettings(updateVersion = "0.1.1", updateUrl = RELEASES_PAGE)
        assertEquals("0.1.1", AvailableUpdate.from(s, "0.1")?.version)
        assertNull(AvailableUpdate.from(s, "0.1.1"))
        assertNull(AvailableUpdate.from(s, "0.2"))
    }

    @Test
    fun untrustedStoredLinkFallsBackToReleasesPage() {
        val s = AppSettings(updateVersion = "0.2", updateUrl = "https://evil.example.com/x.apk")
        assertEquals(RELEASES_PAGE, AvailableUpdate.from(s, "0.1")?.url)
    }
}
