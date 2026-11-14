package ru.my.traty.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingTextTest {
    @Test
    fun waitingTextAgreesWithCount() {
        assertEquals("1 фраза ждёт разбора", pendingWaitingText(1))
        assertEquals("2 фразы ждут разбора", pendingWaitingText(2))
        assertEquals("4 фразы ждут разбора", pendingWaitingText(4))
        assertEquals("5 фраз ждут разбора", pendingWaitingText(5))
        assertEquals("11 фраз ждут разбора", pendingWaitingText(11))
        assertEquals("21 фраза ждёт разбора", pendingWaitingText(21))
        assertEquals("22 фразы ждут разбора", pendingWaitingText(22))
    }
}
