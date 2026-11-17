package ru.my.traty.backup

import org.junit.Assert.*
import org.junit.Test
import ru.my.traty.data.apartments.*
import java.time.LocalDate

class ApartmentBackupTest {
    @Test fun portableKeysPreserveLinksAndValues() {
        val date = ReadingDates.encode(LocalDate.of(2026, 1, 1))
        val s = ApartmentSnapshot(
            apartments = listOf(Apartment(82, "Дом")),
            meters = listOf(Meter(64, 82, "Кухня", "COLD_WATER", accountingFrom = date)),
            registers = listOf(MeterRegister(92, 64)),
            readings = listOf(MeterReading(74, 92, date, 12345)),
            submissions = listOf(ReadingSubmission(44, 64, date, 202601)),
            submissionReadings = listOf(SubmissionReading(44, 74)),
        )
        val p = ApartmentBackupCodec.portable(s)
        assertEquals(1L, p.apartments.single().id)
        assertEquals(p.meters.single().id, p.registers.single().meterId)
        assertEquals(p.registers.single().id, p.readings.single().registerId)
        assertEquals(p.readings.single().id, p.submissionReadings.single().readingId)
        assertEquals(12345L, p.readings.single().valueMilli)
        assertEquals(p, ApartmentBackupCodec.checked(p))
    }
    @Test(expected = FullBackupException::class)
    fun orphanIsRejectedBeforeRestore() {
        ApartmentBackupCodec.checked(ApartmentSnapshot(registers = listOf(MeterRegister(1, 999))))
    }
}
