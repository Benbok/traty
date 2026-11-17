package ru.my.traty.assistant.apartments

import kotlinx.serialization.Serializable

@Serializable
data class ApartmentQuestion(
    val kind: String = "compare", val apartment: String? = null, val meter: String? = null,
    val resource: String? = null, val register: String? = null,
    val from: String? = null, val to: String? = null,
    val compareFrom: String? = null, val compareTo: String? = null,
)
object ApartmentIntents {
    const val READINGS = "meter_readings"
    const val CONSUMPTION = "resource_consumption"
    const val COMPARE = "resource_compare"
    const val TREND = "resource_trend"
    val ALL = setOf(READINGS, CONSUMPTION, COMPARE, TREND)
}
