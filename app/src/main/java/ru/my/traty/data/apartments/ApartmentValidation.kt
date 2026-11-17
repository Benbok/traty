package ru.my.traty.data.apartments

import java.time.LocalDate
import java.time.YearMonth

/** Единая проверка записи и восстановления копии. Исключение откатывает транзакцию. */
object ApartmentValidation {
    fun check(s: ApartmentSnapshot, today: LocalDate = LocalDate.now()) {
        fun ids(values: List<Long>) = require(values.all { it > 0 } && values.distinct().size == values.size) { "Повторяются записи квартир или счётчиков" }
        ids(s.apartments.map { it.id }); ids(s.meters.map { it.id }); ids(s.registers.map { it.id })
        ids(s.readings.map { it.id }); ids(s.submissions.map { it.id })
        val apartments = s.apartments.associateBy { it.id }
        val meters = s.meters.associateBy { it.id }
        val registers = s.registers.associateBy { it.id }
        val readings = s.readings.associateBy { it.id }
        val submissions = s.submissions.associateBy { it.id }
        require(s.apartments.all { it.name.isNotBlank() }) { "Укажите название квартиры" }
        s.meters.forEach { m ->
            require(m.apartmentId in apartments && m.name.isNotBlank()) { "Проверьте квартиру и название счётчика" }
            require(ResourceType.entries.any { it.name == m.resource }) { "Неизвестный ресурс" }
            require(listOfNotNull(m.accountingFrom, m.accountingTo).all(ReadingDates::normalized)) { "Проверьте даты учёта" }
            require(m.accountingTo == null || m.accountingFrom == null || m.accountingFrom <= m.accountingTo) { "Конец учёта раньше начала" }
            val codes = s.registers.filter { it.meterId == m.id }.map { it.code }
            require(codes.isNotEmpty() && codes.distinct().size == codes.size) { "Проверьте тарифы счётчика" }
            require(codes == listOf("TOTAL") || (m.resource == ResourceType.ELECTRICITY.name && codes.toSet() in listOf(setOf("T1", "T2"), setOf("T1", "T2", "T3")))) { "Выберите один, два или три тарифа" }
        }
        require(s.registers.all { it.meterId in meters }) { "Тариф без счётчика" }
        s.readings.forEach { r ->
            val reg = registers[r.registerId] ?: error("Показание без счётчика")
            val meter = meters.getValue(reg.meterId)
            require(ReadingDates.normalized(r.measuredAt) && ReadingDates.decode(r.measuredAt) <= today && r.valueMilli >= 0) { "Проверьте дату и значение показания" }
            require(meter.accountingFrom == null || r.measuredAt >= meter.accountingFrom) { "Замер раньше начала учёта: исправьте дату начала у счётчика" }
            require(meter.accountingTo == null || r.measuredAt <= meter.accountingTo) { "Замер позже окончания учёта" }
        }
        s.readings.groupBy { it.registerId }.values.forEach { rs -> Consumption.intervals(rs.map { ReadingPoint(it.measuredAt, it.valueMilli) }) }
        s.submissions.forEach { sub ->
            require(sub.meterId in meters && ReadingDates.normalized(sub.submittedAt) && ReadingDates.decode(sub.submittedAt) <= today) { "Проверьте дату передачи" }
            YearMonth.of(sub.month / 100, sub.month % 100)
            val links = s.submissionReadings.filter { it.submissionId == sub.id }
            require(links.isNotEmpty()) { "Для передачи выберите показания" }
            links.forEach { link ->
                val r = readings[link.readingId] ?: error("Переданное показание не найдено")
                require(registers[r.registerId]?.meterId == sub.meterId && r.measuredAt <= sub.submittedAt) { "Проверьте выбранные показания и дату передачи" }
            }
        }
        require(s.submissionReadings.distinct().size == s.submissionReadings.size && s.submissionReadings.all { it.submissionId in submissions && it.readingId in readings }) { "Нарушены связи передачи показаний" }
    }
}
