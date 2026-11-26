package ru.my.traty.ui.apartments

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.data.apartments.Meter
import ru.my.traty.data.apartments.ResourceType

class MeterSummarySubtitleTest {
    @Test
    fun includesSerialNumberWhenPresent() {
        val meter = Meter(
            id = 1L,
            apartmentId = 1L,
            name = "Кухня",
            resource = ResourceType.COLD_WATER.name,
            serialNumber = " 12345678 ",
        )

        assertEquals("Холодная вода · № 12345678", meterSummarySubtitle(meter))
    }

    @Test
    fun avoidsDuplicateNumberSignWhenSerialAlreadyStartsWithIt() {
        val meter = Meter(
            id = 1L,
            apartmentId = 1L,
            name = "Санузел",
            resource = ResourceType.HOT_WATER.name,
            serialNumber = "№ 987654",
            archived = true,
        )

        assertEquals("Архив · Горячая вода · № 987654", meterSummarySubtitle(meter))
    }

    @Test
    fun omitsSerialNumberWhenBlankOrMissing() {
        val withoutSerial = Meter(
            id = 1L,
            apartmentId = 1L,
            name = "Свет",
            resource = ResourceType.ELECTRICITY.name,
            serialNumber = null,
        )
        val blankSerial = withoutSerial.copy(serialNumber = "   ", archived = true)

        assertEquals("Электричество", meterSummarySubtitle(withoutSerial))
        assertEquals("Архив · Электричество", meterSummarySubtitle(blankSerial))
    }
}
