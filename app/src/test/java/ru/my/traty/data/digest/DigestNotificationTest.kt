package ru.my.traty.data.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestNotificationTest {
    @Test
    fun reminderIsIncludedEvenWhenThereIsNoDigest() {
        val message = digestNotification(null, listOf(DigestGoalReminder("Наушники", 40_000L)))
        requireNotNull(message)
        assertTrue(message.body.contains("Наушники"))
        assertTrue(message.body.contains("400"))
        assertTrue(message.opensPurchaseGoals)
    }

    @Test
    fun contributionAlreadyMadeLeavesTheRegularDigest() {
        val digest = DailyDigest(DigestTone.USUAL, "Итог", "Обычный день")
        val message = digestNotification(digest, listOf(DigestGoalReminder("Наушники", 0L)))
        requireNotNull(message)
        assertEquals(digest.body, message.body)
        assertFalse(message.opensPurchaseGoals)
        assertNull(digestNotification(null, emptyList()))
    }

    @Test
    fun severalUnfundedGoalsAreTotaledInTheSameNotification() {
        val digest = DailyDigest(DigestTone.USUAL, "Итог", "Обычный день")
        val message = digestNotification(
            digest,
            listOf(DigestGoalReminder("Наушники", 40_000L), DigestGoalReminder("Планшет", 25_000L)),
        )
        requireNotNull(message)
        assertEquals(digest.title, message.title)
        assertTrue(message.body.contains("650"))
        assertTrue(message.body.contains(digest.body))
    }
}
