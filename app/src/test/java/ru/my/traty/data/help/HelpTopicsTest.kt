package ru.my.traty.data.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTopicsTest {
    @Test
    fun idsAreUniqueAndNotBlank() {
        val ids = HelpTopics.all.map { it.id }
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyTopicHasTextForThePerson() {
        HelpTopics.all.forEach { t ->
            assertTrue("${t.id}: title", t.title.isNotBlank())
            assertTrue("${t.id}: emoji", t.emoji.isNotBlank())
            assertTrue("${t.id}: summary", t.summary.isNotBlank())
            assertTrue("${t.id}: body", t.body.isNotEmpty() && t.body.all { it.isNotBlank() })
        }
    }

    @Test
    fun textsAreWrittenInRussianWithoutTechnicalWords() {
        // Правило CLAUDE.md: тексты для пользователя понятные, без технических терминов
        val banned = listOf("API", "SQL", "JSON", "Room", "DataStore", "Keystore", "ViewModel")
        HelpTopics.all.forEach { t ->
            val all = (listOf(t.title, t.summary) + t.body + listOfNotNull(t.where)).joinToString(" ")
            banned.forEach { w -> assertTrue("${t.id}: «$w»", !all.contains(w)) }
        }
    }

    @Test
    fun byIdFindsTopicOrReturnsNull() {
        assertNotNull(HelpTopics.byId("voice"))
        assertNull(HelpTopics.byId("no-such-topic"))
    }
}
