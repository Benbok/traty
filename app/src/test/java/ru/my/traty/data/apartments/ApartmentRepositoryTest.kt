package ru.my.traty.data.apartments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.Category
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ApartmentRepositoryTest {
    private fun date(s: String) = ReadingDates.encode(LocalDate.parse(s))

    @Test fun readingsAreIndependentAndEditingClearsSubmission() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = ApartmentRepository(db)
            val a = repo.saveApartment(Apartment(name = "Дом"))
            val b = repo.saveApartment(Apartment(name = "Дача"))
            val meter = repo.saveMeter(Meter(apartmentId = a, name = "Кухня", resource = "COLD_WATER"), listOf("TOTAL"))
            repo.saveMeter(Meter(apartmentId = b, name = "Кухня", resource = "COLD_WATER"), listOf("TOTAL"))
            val register = repo.snapshot().registers.first { it.meterId == meter }
            repo.saveReadings(listOf(MeterReading(registerId = register.id, measuredAt = date("2026-01-01"), valueMilli = 1000)))
            val r = repo.snapshot().readings.single()
            repo.markSubmitted(meter, listOf(r.id), date("2026-01-02"), 202601)
            repo.saveReadings(listOf(r.copy(valueMilli = 2000)))
            assertTrue(repo.snapshot().submissions.isEmpty())
            assertEquals(2, repo.snapshot().apartments.size)
            assertEquals(1, repo.snapshot().readings.size)
            try {
                repo.saveReadings(listOf(r.copy(id = 0, valueMilli = 3000)))
                fail("Повтор даты должен отклоняться")
            } catch (_: Exception) { }
            assertEquals(1, repo.snapshot().readings.size)
        } finally { db.close() }
    }

    @Test fun archivingAndRestoringPreserveMetersAndReadingHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = ApartmentRepository(db)
            val apartmentId = repo.saveApartment(Apartment(name = "Дом"))
            val meterId = repo.saveMeter(
                Meter(apartmentId = apartmentId, name = "Кухня", resource = "COLD_WATER", accountingTo = date("2026-02-01")),
                listOf("TOTAL"),
            )
            val registerId = repo.snapshot().registers.single().id
            repo.saveReadings(listOf(MeterReading(registerId = registerId, measuredAt = date("2026-01-01"), valueMilli = 1000)))
            val readingId = repo.snapshot().readings.single().id
            repo.markSubmitted(meterId, listOf(readingId), date("2026-01-02"), 202601)
            val original = repo.snapshot()

            repo.setApartmentArchived(apartmentId, true)
            val withArchivedApartment = original.copy(apartments = original.apartments.map { it.copy(archived = true) })
            assertEquals(withArchivedApartment, repo.snapshot())

            repo.setMeterArchived(meterId, true)
            val withArchivedBoth = withArchivedApartment.copy(meters = original.meters.map { it.copy(archived = true) })
            assertEquals(withArchivedBoth, repo.snapshot())

            repo.setApartmentArchived(apartmentId, false)
            assertTrue(repo.snapshot().meters.single().archived)
            repo.setMeterArchived(meterId, false)
            assertEquals(original, repo.snapshot())
        } finally { db.close() }
    }

    @Test fun migrationAddsTablesAndPreservesExistingCategories() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "apartments-migration-test.db"
        context.deleteDatabase(name)
        val original = AppDatabase.build(context, name)
        original.categories().insert(Category(name = "Не потерять", emoji = "✓"))
        val sql = original.openHelper.writableDatabase
        listOf(
            "purchase_contributions", "purchase_goals",
            "submission_readings", "reading_submissions", "meter_readings", "meter_registers", "meters", "apartments",
        ).forEach { sql.execSQL("DROP TABLE `$it`") }
        sql.version = 16
        original.close()
        val migrated = AppDatabase.build(context, name)
        try {
            assertTrue(migrated.categories().getAllWithSubs().any { it.category.name == "Не потерять" })
            assertTrue(migrated.apartments().apartments().isEmpty())
            assertTrue(migrated.apartments().readings().isEmpty())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
