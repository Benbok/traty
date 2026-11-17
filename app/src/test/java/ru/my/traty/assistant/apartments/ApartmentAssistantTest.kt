package ru.my.traty.assistant.apartments

import org.junit.Assert.*
import org.junit.Test
import ru.my.traty.data.apartments.*
import java.time.LocalDate

class ApartmentAssistantTest {
    @Test fun financeQuestionIsNotCaptured() {
        assertNull(ApartmentQuestionRules.resolve("Сколько заплатил за воду?", null, null, LocalDate.of(2026, 11, 17)))
        assertNotNull(ApartmentQuestionRules.resolve("Как изменилось потребление воды?", null, null, LocalDate.of(2026, 11, 17)))
    }
    @Test fun lastReadingDoesNotRequireThreePoints() {
        val day = ReadingDates.encode(LocalDate.of(2026, 1, 1))
        val s = ApartmentSnapshot(listOf(Apartment(1, "Дом")), listOf(Meter(1, 1, "Кухня", "COLD_WATER", accountingFrom = day)), listOf(MeterRegister(1, 1)), listOf(MeterReading(1, 1, day, 12345)))
        val reply = ApartmentAssistant.answer(ApartmentQuestion(kind = "readings"), s)
        assertTrue(reply.contains("12,345"))
        assertTrue(reply.contains("2026-01-01"))
        assertTrue(ApartmentAssistant.answer(ApartmentQuestion(), s).contains("Недостаточно"))
    }
    @Test fun followUpRetainsResourceButChangesPeriod() {
        val old = ApartmentQuestion(apartment = "Дом", resource = "HOT_WATER")
        val q = ApartmentQuestionRules.resolve("А за октябрь?", null, old, LocalDate.of(2026, 11, 17))
        assertEquals("Дом", q?.apartment)
        assertEquals("HOT_WATER", q?.resource)
        assertEquals("2026-10-01", q?.from)
    }
    @Test fun ambiguousApartmentNameNeedsClarification() {
        val s = ApartmentSnapshot(apartments = listOf(Apartment(1, "Дом"), Apartment(2, "Дом")))
        assertTrue(ApartmentAssistant.answer(ApartmentQuestion(apartment = "Дом"), s).contains("Уточните"))
    }
    @Test fun financialDatesAreNotInventedForConsumption() {
        val q = ApartmentQuestionRules.resolve("Сравни электричество", null, null, LocalDate.of(2026, 11, 17))
        assertNull(q?.from)
        assertEquals("ELECTRICITY", q?.resource)
    }

    @Test fun moneyPhrasingAndUnrelatedFollowUpRemainFinancial() {
        val previous = ApartmentQuestion(resource = "HOT_WATER", apartment = "Дом")
        val today = LocalDate.of(2026, 11, 17)
        assertNull(ApartmentQuestionRules.resolve("Сколько ушло на воду за октябрь?", null, null, today))
        assertNull(ApartmentQuestionRules.resolve("А сколько ушло на такси?", null, previous, today))
    }
    @Test fun onlineFollowUpMergesSelectorsInsteadOfLosingThem() {
        val old = ApartmentQuestion(apartment = "Дом", meter = "Ванная", resource = "HOT_WATER")
        val ai = ru.my.traty.assistant.AssistantQuery(intent = ApartmentIntents.CONSUMPTION,
            housing = ApartmentQuestion(from = "2026-10-01", to = "2026-10-31"))
        val q = ApartmentQuestionRules.resolve("А за октябрь?", ai, old, LocalDate.of(2026, 11, 17))
        assertEquals("Дом", q?.apartment)
        assertEquals("Ванная", q?.meter)
        assertEquals("HOT_WATER", q?.resource)
        assertEquals("2026-10-01", q?.from)
    }

    @Test fun waterScopeDoesNotIncludeElectricityAndGas() {
        val day = ReadingDates.encode(LocalDate.of(2026, 1, 1))
        val s = ApartmentSnapshot(listOf(Apartment(1, "Дом")), listOf(
            Meter(1, 1, "Кухня", "COLD_WATER", accountingFrom = day),
            Meter(2, 1, "Электричество", "ELECTRICITY", accountingFrom = day)),
            listOf(MeterRegister(1, 1), MeterRegister(2, 2)), listOf(MeterReading(1, 1, day, 1000), MeterReading(2, 2, day, 2000)))
        val q = ApartmentQuestionRules.resolve("Последние показания воды", null, null, LocalDate.of(2026, 11, 17))
        val answer = ApartmentAssistant.answer(requireNotNull(q), s)
        assertTrue(answer.contains("Холодная вода"))
        assertFalse(answer.contains("Электричество"))
    }
    @Test fun tariffRegisterQuestionIsNotAMoneyQuestion() {
        val q = ApartmentQuestionRules.resolve("Покажи показания второго тарифа", null, null, LocalDate.of(2026, 11, 17))
        assertEquals("T2", q?.register)
        assertEquals("readings", q?.kind)
    }
}
