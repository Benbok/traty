package ru.my.traty.ui.apartments

import android.app.Application
import ru.my.traty.data.apartments.MeterReading

class MeterDetailViewModel(app: Application) : HousingViewModel(app) {
    fun save(values: List<MeterReading>, onSaved: () -> Unit) = change(onSaved) { repo.saveReadings(values) }
    fun delete(id: Long, onSaved: () -> Unit) = change(onSaved) { repo.deleteReading(id) }
    fun submitted(meterId: Long, ids: List<Long>, date: Long, month: Int, onSaved: () -> Unit) = change(onSaved) { repo.markSubmitted(meterId, ids, date, month) }
}
