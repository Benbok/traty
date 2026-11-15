package ru.my.traty.data.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.help.HelpTopics
import ru.my.traty.data.update.AppVersion

class WhatsNewPolicyTest {
    private fun entry(v: String) = WhatsNewEntry(v, listOf(WhatsNewItem("Новинка $v")))
    private val entries = listOf(entry("0.3"), entry("0.2"), entry("0.1.1"))

    private fun decide(installed: String, lastSeen: String?, hasRecords: Boolean = true) =
        WhatsNewPolicy.decide(installed, lastSeen, hasRecords, entries)

    @Test
    fun newcomerIsNotShownAnythingButVersionIsRemembered() {
        val d = decide("0.3", lastSeen = null, hasRecords = false)
        assertTrue(d.toShow.isEmpty())
        assertEquals("0.3", d.recordVersion)
    }

    @Test
    fun updatedPersonWithoutRecordedVersionSeesLatestEntry() {
        val d = decide("0.3", lastSeen = null)
        assertEquals(listOf("0.3"), d.toShow.map { it.version })
        assertEquals("0.3", d.recordVersion)
    }

    @Test
    fun updateSkippingVersionsShowsEverythingSinceLastSeen() {
        val d = decide("0.3", lastSeen = "0.1.1")
        assertEquals(listOf("0.3", "0.2"), d.toShow.map { it.version })
    }

    @Test
    fun entryNewerThanInstalledIsNotShown() {
        val d = decide("0.2", lastSeen = "0.1.1")
        assertEquals(listOf("0.2"), d.toShow.map { it.version })
    }

    @Test
    fun sameVersionOrDowngradeShowsNothingAndRecordsNothing() {
        assertEquals(WhatsNewDecision.NOTHING, decide("0.3", lastSeen = "0.3"))
        assertEquals(WhatsNewDecision.NOTHING, decide("0.2", lastSeen = "0.3"))
    }

    @Test
    fun updateWithoutEntryRecordsVersionQuietly() {
        val d = decide("0.2.1", lastSeen = "0.2")
        assertTrue(d.toShow.isEmpty())
        assertEquals("0.2.1", d.recordVersion)
    }

    @Test
    fun testOrBrokenVersionIsIgnored() {
        assertEquals(WhatsNewDecision.NOTHING, decide("0.4-test", lastSeen = "0.2"))
        assertEquals(WhatsNewDecision.NOTHING, decide("", lastSeen = null))
    }

    @Test
    fun brokenRecordedVersionIsTreatedAsMissing() {
        assertEquals(listOf("0.3"), decide("0.3", lastSeen = "мусор").toShow.map { it.version })
    }

    @Test
    fun releasedEntriesAreWellFormed() {
        val versions = WhatsNew.entries.map { it.version }
        assertTrue("версии записей должны разбираться", versions.all { AppVersion.parse(it) != null })
        assertEquals("версии не повторяются", versions.size, versions.toSet().size)
        // Свежие сверху: следующая запись не новее предыдущей
        versions.zipWithNext().forEach { (a, b) -> assertTrue("$a выше $b", !AppVersion.isNewer(b, a)) }
        WhatsNew.entries.flatMap { it.items }.forEach { item ->
            assertTrue(item.text.isNotBlank())
            item.topicId?.let { assertTrue("тема «$it» есть в справке", HelpTopics.byId(it) != null) }
        }
        assertNull(WhatsNew.entries.firstOrNull { it.items.isEmpty() })
    }
}
