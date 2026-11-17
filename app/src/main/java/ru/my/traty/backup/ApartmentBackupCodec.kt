package ru.my.traty.backup

import ru.my.traty.data.apartments.ApartmentSnapshot
import ru.my.traty.data.apartments.ApartmentValidation

/** Перенумеровывает ID в локальные ключи файла, не связанные с ID на другом телефоне. */
object ApartmentBackupCodec {
    fun portable(s: ApartmentSnapshot): ApartmentSnapshot {
        ApartmentValidation.check(s)
        fun keys(ids: List<Long>) = ids.mapIndexed { index, id -> id to index + 1L }.toMap()
        val a = keys(s.apartments.map { it.id }); val m = keys(s.meters.map { it.id })
        val r = keys(s.registers.map { it.id }); val v = keys(s.readings.map { it.id }); val t = keys(s.submissions.map { it.id })
        return ApartmentSnapshot(
            s.apartments.map { it.copy(id = a.getValue(it.id)) },
            s.meters.map { it.copy(id = m.getValue(it.id), apartmentId = a.getValue(it.apartmentId)) },
            s.registers.map { it.copy(id = r.getValue(it.id), meterId = m.getValue(it.meterId)) },
            s.readings.map { it.copy(id = v.getValue(it.id), registerId = r.getValue(it.registerId)) },
            s.submissions.map { it.copy(id = t.getValue(it.id), meterId = m.getValue(it.meterId)) },
            s.submissionReadings.map { it.copy(submissionId = t.getValue(it.submissionId), readingId = v.getValue(it.readingId)) },
        )
    }
    fun checked(s: ApartmentSnapshot): ApartmentSnapshot = try {
        ApartmentValidation.check(s); s
    } catch (e: IllegalArgumentException) {
        throw FullBackupException("Не удалось восстановить историю квартир: проверьте показания и связи в копии.")
    } catch (e: IllegalStateException) {
        throw FullBackupException("Не удалось восстановить историю квартир: нарушены связи в копии.")
    }
}
