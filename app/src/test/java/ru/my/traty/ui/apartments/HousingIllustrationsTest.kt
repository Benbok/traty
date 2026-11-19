package ru.my.traty.ui.apartments

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.R
import ru.my.traty.data.apartments.ResourceType

class HousingIllustrationsTest {
    @Test
    fun eachResourceTypeHasItsOwnIllustration() {
        val expected = mapOf(
            ResourceType.COLD_WATER to R.drawable.ic_housing_cold_water,
            ResourceType.HOT_WATER to R.drawable.ic_housing_hot_water,
            ResourceType.ELECTRICITY to R.drawable.ic_housing_electricity,
            ResourceType.GAS to R.drawable.ic_housing_gas,
        )

        assertEquals(expected, ResourceType.entries.associateWith { it.illustration })
        assertEquals(expected.size, expected.values.toSet().size)
    }
}
