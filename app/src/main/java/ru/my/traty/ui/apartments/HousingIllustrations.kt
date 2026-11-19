package ru.my.traty.ui.apartments

import androidx.annotation.DrawableRes
import ru.my.traty.R
import ru.my.traty.data.apartments.ResourceType

@get:DrawableRes
internal val ResourceType.illustration: Int
    get() = when (this) {
        ResourceType.COLD_WATER -> R.drawable.ic_housing_cold_water
        ResourceType.HOT_WATER -> R.drawable.ic_housing_hot_water
        ResourceType.ELECTRICITY -> R.drawable.ic_housing_electricity
        ResourceType.GAS -> R.drawable.ic_housing_gas
    }
