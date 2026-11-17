package ru.my.traty.ui.apartments

import android.app.Application
import ru.my.traty.data.apartments.Apartment

class ApartmentsViewModel(app: Application) : HousingViewModel(app) {
    fun save(value: Apartment, onSaved: () -> Unit) = change(onSaved) { repo.saveApartment(value) }
}
