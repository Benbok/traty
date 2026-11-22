package ru.my.traty.data.apartments

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.my.traty.data.AppDatabase

class ApartmentRepository(private val db: AppDatabase) {
    private val dao get() = db.apartments()
    fun observe(): Flow<ApartmentSnapshot> = combine(dao.observeApartments(), dao.observeMeters(), dao.observeReadings(), dao.observeSubmissions()) { _, _, _, _ -> Unit }.map { snapshot() }
    suspend fun snapshot(): ApartmentSnapshot = db.withTransaction {
        ApartmentSnapshot(dao.apartments(), dao.meters(), dao.registers(), dao.readings(), dao.submissions(), dao.submissionReadings())
    }
    suspend fun saveApartment(value: Apartment): Long = db.withTransaction {
        require(value.name.isNotBlank()) { "Укажите название квартиры" }
        val id = if (value.id == 0L) dao.insertApartment(value.copy(name = value.name.trim())) else {
            require(dao.apartments().any { it.id == value.id }) { "Квартира не найдена" }
            dao.updateApartment(value.copy(name = value.name.trim())); value.id
        }
        ApartmentValidation.check(snapshot()); id
    }
    suspend fun saveMeter(value: Meter, codes: List<String>): Long = db.withTransaction {
        val id = if (value.id == 0L) {
            val id = dao.insertMeter(value.copy(name = value.name.trim()))
            codes.forEach { dao.insertRegister(MeterRegister(meterId = id, code = it)) }; id
        } else {
            val old = dao.meters().firstOrNull { it.id == value.id } ?: error("Счётчик не найден")
            require(old.apartmentId == value.apartmentId) { "Нельзя переносить историю в другую квартиру" }
            require(old.resource == value.resource && dao.registers().filter { it.meterId == value.id }.map { it.code }.toSet() == codes.toSet()) { "Для другого ресурса или набора тарифов создайте новый счётчик" }
            dao.updateMeter(value.copy(name = value.name.trim())); value.id
        }
        ApartmentValidation.check(snapshot()); id
    }
    suspend fun setApartmentArchived(id: Long, archived: Boolean) = db.withTransaction {
        val apartment = requireNotNull(dao.apartments().firstOrNull { it.id == id }) { "Квартира не найдена" }
        if (apartment.archived != archived) dao.updateApartment(apartment.copy(archived = archived))
    }

    suspend fun setMeterArchived(id: Long, archived: Boolean) = db.withTransaction {
        val meter = requireNotNull(dao.meters().firstOrNull { it.id == id }) { "Счётчик не найден" }
        if (meter.archived != archived) dao.updateMeter(meter.copy(archived = archived))
    }

    suspend fun saveReadings(values: List<MeterReading>) = db.withTransaction {
        require(values.isNotEmpty()) { "Введите хотя бы одно показание" }
        values.forEach { r ->
            if (r.id == 0L) dao.insertReading(r) else {
                val old = dao.readings().firstOrNull { it.id == r.id } ?: error("Показание не найдено")
                require(old.registerId == r.registerId) { "Нельзя переносить показание к другому счётчику" }
                if (old.valueMilli != r.valueMilli || old.measuredAt != r.measuredAt) dao.clearSubmissionForReading(r.id)
                dao.updateReading(r.copy(createdAt = old.createdAt, updatedAt = System.currentTimeMillis()))
            }
        }
        val snapshot = snapshot()
        val touched = values.mapNotNull { r -> snapshot.registers.firstOrNull { it.id == r.registerId }?.meterId }.toSet()
        snapshot.meters.filter { it.id in touched && it.accountingFrom == null }.forEach { meter ->
            val ids = snapshot.registers.filter { it.meterId == meter.id }.map { it.id }.toSet()
            val first = snapshot.readings.filter { it.registerId in ids }.minOfOrNull { it.measuredAt }
            dao.updateMeter(meter.copy(accountingFrom = first))
        }
        ApartmentValidation.check(snapshot())
    }
    suspend fun deleteReading(id: Long) = db.withTransaction {
        dao.clearSubmissionForReading(id); dao.deleteReading(id)
    }
    suspend fun markSubmitted(meterId: Long, readingIds: List<Long>, date: Long, month: Int) = db.withTransaction {
        require(readingIds.isNotEmpty() && readingIds.distinct().size == readingIds.size) { "Выберите показания" }
        val id = dao.insertSubmission(ReadingSubmission(meterId = meterId, submittedAt = date, month = month))
        readingIds.forEach { dao.insertSubmissionReading(SubmissionReading(id, it)) }
        ApartmentValidation.check(snapshot())
    }
}
