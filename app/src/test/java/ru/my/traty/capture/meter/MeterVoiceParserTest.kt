package ru.my.traty.capture.meter

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MeterVoiceParserTest {
    private val today = LocalDate.of(2026, 11, 18)
    private fun parse(text: String, codes: List<String> = listOf("TOTAL")) = MeterVoiceParser.parse(text, codes, today)

    @Test fun parsesExactDecimalsAndLeadingFractionalZeros() {
        mapOf(
            "123,45" to 123_450L,
            "сто двадцать три запятая сорок пять" to 123_450L,
            "один два три запятая ноль пять" to 123_050L,
            "123 запятая 05" to 123_050L,
            "123 точка ноль ноль пять" to 123_005L,
            "ноль запятая ноль" to 0L,
            "1 234,005" to 1_234_005L,
            "девять миллионов восемьсот тысяч пять" to 9_800_005_000L,
        ).forEach { (text, expected) -> assertEquals(text, expected, parse(text).valuesMilli.getValue("TOTAL")) }
    }

    @Test fun preservesLongPrecisionWithoutDouble() {
        assertEquals(9_007_199_254_740_993L, parse("9007199254740,993").valuesMilli.getValue("TOTAL"))
    }

    @Test fun parsesAllTariffsAndDoesNotInventMissingOnes() {
        val result = parse("тариф один — пять тысяч двести, и тариф два — тысяча восемьсот", listOf("T1", "T2", "T3"))
        assertEquals(mapOf("T1" to 5_200_000L, "T2" to 1_800_000L), result.valuesMilli)
        assertEquals(mapOf("T3" to 300_005L), parse("Т3: 300,005", listOf("T1", "T2", "T3")).valuesMilli)
        assertEquals(mapOf("T1" to 123_000L), parse("123", listOf("T1")).valuesMilli)
    }

    @Test fun extractsDateWithoutTreatingDayOrYearAsReading() {
        assertEquals(MeterVoiceDraft(mapOf("TOTAL" to 123_000L), LocalDate.of(2026, 11, 15)),
            parse("за пятнадцатое ноября: сто двадцать три"))
        assertEquals(LocalDate.of(2026, 10, 25), parse("123 за двадцать пятое октября").date)
        assertEquals(LocalDate.of(2025, 11, 15), parse("за 15 ноября 2025 года 123").date)
        assertEquals(LocalDate.of(2026, 11, 15), parse("за 15.11.2026: 123").date)
        assertEquals(today.minusDays(1), parse("вчера 123").date)
        assertNull(parse("показания 123").date)
    }

    @Test fun ambiguousNumbersAndMalformedFractionsAreRejected() {
        listOf("123 45", "123 и 45", "сто двадцать пятнадцать", "минус 123", "-123", "123,4567",
            "123 запятая", "123 запятая ноль ноль ноль пять", "123 или 124", "123 нет 124",
            "полторы тысячи", "двадцать пятнадцать", "99999999999999999999999",
            "тысяча тысяча", "горячая вода 123", "123 рубля", "").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { parse(it) }
        }
    }

    @Test fun rejectsWrongRepeatedOrUnspecifiedTariffAndRejectsWholeBatch() {
        listOf("123", "Т3 123", "Т1 123 Т1 124", "Т1 123 Т2 непонятно", "123 Т1 456").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { parse(it, listOf("T1", "T2")) }
        }
        assertThrows(IllegalArgumentException::class.java) { parse("Т1 123") }
    }

    @Test fun rejectsInvalidFutureAndMultipleDates() {
        listOf("за 31.11.2026 123", "за 19 ноября 123", "сегодня вчера 123", "за 15 ноября 20260 1").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { parse(it) }
        }
    }
}
