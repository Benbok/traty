package ru.my.traty.data.apartments

import kotlinx.serialization.Serializable

/** Снимок также используется для безопасного переноса и проверки внешних ключей. */
@Serializable
data class ApartmentSnapshot(
    val apartments: List<Apartment> = emptyList(), val meters: List<Meter> = emptyList(),
    val registers: List<MeterRegister> = emptyList(), val readings: List<MeterReading> = emptyList(),
    val submissions: List<ReadingSubmission> = emptyList(), val submissionReadings: List<SubmissionReading> = emptyList(),
) {
    fun series(meterIds: Set<Long>): List<RegisterSeries> = registers.filter { it.meterId in meterIds }.map { register ->
        val meter = meters.first { it.id == register.meterId }
        RegisterSeries(register.id, meter.id, register.code,
            readings.filter { it.registerId == register.id }.map { ReadingPoint(it.measuredAt, it.valueMilli) },
            meter.accountingFrom, meter.accountingTo, meter.archived)
    }
}
