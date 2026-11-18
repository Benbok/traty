package ru.my.traty.data.apartments

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SubmissionHistoryTest {
    @Test
    fun hidesAlreadySubmittedReadingsRegardlessOfReportingMonth() {
        val day = ReadingDates.encode(LocalDate.of(2026, 11, 1))
        val submitted = MeterReading(id = 1, registerId = 1, measuredAt = day, valueMilli = 100)
        val pending = MeterReading(id = 2, registerId = 2, measuredAt = day, valueMilli = 200)
        val marks = mapOf(1L to listOf(ReadingSubmission(1, 2, day, 202610)))

        assertEquals(listOf(pending), unsubmittedReadings(listOf(submitted, pending), marks.keys))
    }

    @Test
    fun historyIncludesReadingsTakenInTheSelectedMonthEvenWithoutSubmission() {
        fun reading(id: Long, day: LocalDate) =
            MeterReading(id = id, registerId = 1, measuredAt = ReadingDates.encode(day), valueMilli = id)
        val before = reading(1, LocalDate.of(2026, 9, 30))
        val first = reading(2, LocalDate.of(2026, 10, 1))
        val last = reading(3, LocalDate.of(2026, 10, 31))
        val after = reading(4, LocalDate.of(2026, 11, 1))
        val readings = listOf(before, first, last, after)

        assertEquals(listOf(first, last), readingsForMonth(readings, YearMonth.of(2026, 10)))
        assertEquals(readings, readingsForMonth(readings, null))
    }

    @Test
    fun preservesSubmissionMarksAcrossDifferentReportingMonths() {
        val submittedAt = ReadingDates.encode(LocalDate.of(2026, 11, 2))
        val october = ReadingSubmission(1, 2, submittedAt, 202610)
        val november = ReadingSubmission(2, 2, submittedAt, 202611)

        val marks = submissionsByReading(
            listOf(october, november),
            listOf(SubmissionReading(1, 10), SubmissionReading(2, 10), SubmissionReading(2, 11)),
        )

        assertEquals(mapOf(10L to listOf(october, november), 11L to listOf(november)), marks)
    }
}
