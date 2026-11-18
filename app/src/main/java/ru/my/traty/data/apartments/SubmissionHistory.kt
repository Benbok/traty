package ru.my.traty.data.apartments

import java.time.YearMonth

internal fun readingsForMonth(readings: List<MeterReading>, month: YearMonth?): List<MeterReading> =
    if (month == null) readings
    else readings.filter { YearMonth.from(ReadingDates.decode(it.measuredAt)) == month }

internal fun unsubmittedReadings(readings: List<MeterReading>, submittedIds: Set<Long>): List<MeterReading> =
    readings.filterNot { it.id in submittedIds }

internal fun submissionsByReading(
    submissions: List<ReadingSubmission>,
    links: List<SubmissionReading>,
): Map<Long, List<ReadingSubmission>> {
    val byId = submissions.associateBy { it.id }
    return links.mapNotNull { link ->
        byId[link.submissionId]?.let { link.readingId to it }
    }.groupBy({ it.first }, { it.second })
}
