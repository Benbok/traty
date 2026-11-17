package ru.my.traty.ui.apartments

import android.app.Application
import ru.my.traty.data.apartments.Meter

class ApartmentDetailViewModel(app: Application) : HousingViewModel(app) {
    fun saveMeter(value: Meter, codes: List<String>, onSaved: () -> Unit) = change(onSaved) { repo.saveMeter(value, codes) }
}
