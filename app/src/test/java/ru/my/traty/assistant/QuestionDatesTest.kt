package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuestionDatesTest {
    /** Суббота. */
    private val today = LocalDate.of(2026, 11, 14)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun find(text: String, startDay: Int = 1) = QuestionDates.find(text, today, startDay)
    private fun one(text: String, startDay: Int = 1): DateSpan {
        val all = find(text, startDay)
        assertEquals("$text → $all", 1, all.size)
        return all.single()
    }

    @Test
    fun namedMonthIsWholeMonthAndNeverInFuture() {
        assertEquals(DateSpan(d(2026, 10, 1), d(2026, 10, 31)), one("сколько я потратил на такси в октябре?"))
        // Декабрь ещё не наступил — значит прошлогодний
        assertEquals(DateSpan(d(2025, 12, 1), d(2025, 12, 31)), one("а в декабре?"))
        assertEquals(DateSpan(d(2026, 5, 1), d(2026, 5, 31)), one("траты за май"))
        assertEquals(DateSpan(d(2026, 3, 1), d(2026, 3, 31)), one("что было в марте"))
        assertEquals(DateSpan(d(2025, 3, 1), d(2025, 3, 31)), one("в марте прошлого года"))
        assertEquals(DateSpan(d(2025, 10, 1), d(2025, 10, 31)), one("октябрь 2025"))
        // Текущий месяц — до сегодня
        assertEquals(DateSpan(d(2026, 11, 1), today), one("в ноябре"))
    }

    @Test
    fun wordsThatLookLikeMonthsAreIgnored() {
        assertTrue(find("сколько стоил маяк и майка").isEmpty())
        assertTrue(find("сколько я трачу на кафе").isEmpty())
        // Слова с началом, как у месяца или времени года, — это товары, а не даты
        assertTrue(find("сколько я потратил на мартини").isEmpty())
        assertTrue(find("сколько ушло на зимнюю куртку").isEmpty())
        assertTrue(find("траты на летние шины и осеннюю обувь").isEmpty())
        assertTrue(find("сколько стоил августовский билет").isEmpty())
    }

    @Test
    fun daysAndRanges() {
        assertEquals(DateSpan(d(2026, 11, 13), d(2026, 11, 13)), one("сколько я потратил вчера"))
        assertEquals(DateSpan(d(2026, 11, 12), d(2026, 11, 12)), one("позавчера"))
        assertEquals(DateSpan(today, today), one("что я купил сегодня"))
        assertEquals(DateSpan(d(2026, 11, 5), d(2026, 11, 5)), one("покупки 5 ноября"))
        assertEquals(DateSpan(d(2026, 10, 1), d(2026, 10, 10)), one("траты с 1 по 10 октября"))
        assertEquals(DateSpan(d(2026, 10, 25), d(2026, 11, 3)), one("с 25 октября по 3 ноября"))
    }

    @Test
    fun weeksAndLastN() {
        assertEquals(DateSpan(d(2026, 11, 9), today), one("на этой неделе"))
        assertEquals(DateSpan(d(2026, 11, 2), d(2026, 11, 8)), one("на прошлой неделе"))
        assertEquals(DateSpan(d(2026, 11, 8), today), one("топ покупок за неделю"))
        assertEquals(DateSpan(d(2026, 8, 15), today), one("за последние 3 месяца"))
        assertEquals(DateSpan(d(2026, 11, 1), today), one("за две недели"))
        assertEquals(DateSpan(d(2026, 5, 15), today), one("за полгода"))
        assertEquals(DateSpan(d(2025, 11, 15), today), one("за последний год"))
        // «за месяц» без «последний» — обычно «в этом месяце»: решает ИИ
        assertTrue(find("сколько я потратил за месяц").isEmpty())
    }

    @Test
    fun billingPeriodRespectsStartDay() {
        assertEquals(DateSpan(d(2026, 11, 10), today), one("в этом месяце", startDay = 10))
        assertEquals(DateSpan(d(2026, 10, 10), d(2026, 11, 9)), one("в прошлом месяце", startDay = 10))
        assertEquals(DateSpan(d(2026, 9, 10), d(2026, 10, 9)), one("в позапрошлом месяце", startDay = 10))
    }

    @Test
    fun yearsAndSeasons() {
        assertEquals(DateSpan(d(2026, 1, 1), today), one("в этом году"))
        assertEquals(DateSpan(d(2025, 1, 1), d(2025, 12, 31)), one("в прошлом году"))
        assertEquals(DateSpan(d(2025, 1, 1), d(2025, 12, 31)), one("за 2025 год"))
        assertEquals(DateSpan(d(2026, 6, 1), d(2026, 8, 31)), one("летом"))
        assertEquals(DateSpan(d(2026, 9, 1), today), one("осенью"))
        // Зима 2026 ещё не началась — берём прошлую, с декабря по февраль
        assertEquals(DateSpan(d(2025, 12, 1), d(2026, 2, 28)), one("зимой"))
        assertEquals(DateSpan(d(2025, 6, 1), d(2025, 8, 31)), one("прошлым летом"))
        assertEquals(DateSpan(QuestionDates.ALL_TIME, today), one("за всё время"))
    }

    @Test
    fun compareTakesTwoSpansLaterFirst() {
        val q = QuestionDates.apply("сравни продукты лето и осень", AssistantQuery(intent = Intents.COMPARE, categories = listOf("Продукты")), today, 1)
        assertEquals("2026-09-01", q.from)
        assertEquals("2026-11-14", q.to)
        assertEquals("2026-06-01", q.compareFrom)
        assertEquals("2026-08-31", q.compareTo)
        assertEquals(listOf("Продукты"), q.categories)
    }

    @Test
    fun applyOverridesModelDatesOnlyWhenQuestionNamesThem() {
        val wrong = AssistantQuery(intent = Intents.TOTAL, from = "2026-12-01", to = "2026-12-31")
        val fixed = QuestionDates.apply("а в декабре?", wrong, today, 1)
        assertEquals("2025-12-01", fixed.from)
        assertEquals("2025-12-31", fixed.to)
        // Срока в вопросе нет — даты из уточнения остаются
        val kept = QuestionDates.apply("а на такси?", AssistantQuery(intent = Intents.TOTAL, from = "2026-10-01", to = "2026-10-31"), today, 1)
        assertEquals("2026-10-01", kept.from)
        // Двух месяцев в вопросе без сравнения — общий отрезок
        val both = QuestionDates.apply("сколько ушло в сентябре и октябре", AssistantQuery(intent = Intents.TOTAL), today, 1)
        assertEquals("2026-09-01", both.from)
        assertEquals("2026-10-31", both.to)
        // У плана и подписок свой срок
        val plan = AssistantQuery(intent = Intents.PLAN)
        assertEquals(plan, QuestionDates.apply("как я иду по плану в этом месяце", plan, today, 1))
    }

    @Test
    fun fixFutureShiftsModelYearBack() {
        val q = QuestionDates.fixFuture(AssistantQuery(intent = Intents.TOTAL, from = "2026-12-01", to = "2026-12-31"), today)
        assertEquals("2025-12-01", q.from)
        assertEquals("2025-12-31", q.to)
        val ok = AssistantQuery(intent = Intents.TOTAL, from = "2026-10-01", to = "2026-10-31")
        assertEquals(ok, QuestionDates.fixFuture(ok, today))
        assertNull(QuestionDates.fixFuture(AssistantQuery(intent = Intents.TOTAL), today).from)
    }
}
