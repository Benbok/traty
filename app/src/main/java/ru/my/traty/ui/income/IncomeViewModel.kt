package ru.my.traty.ui.income

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.Income

/** Действия с доходом из ленты «Трат». */
class IncomeViewModel(app: Application) : AndroidViewModel(app) {
    private val incomes = (app as TratyApp).container.incomes

    fun delete(income: Income) {
        viewModelScope.launch { incomes.delete(income) }
    }
}
