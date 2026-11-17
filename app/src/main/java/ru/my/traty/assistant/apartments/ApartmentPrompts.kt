package ru.my.traty.assistant.apartments

import ru.my.traty.data.apartments.ApartmentSnapshot

object ApartmentPrompts {
    fun catalog(s: ApartmentSnapshot): String = buildString {
        appendLine("Также доступны вопросы о показаниях и потреблении ресурсов. Для них заполняй housing: {kind: readings/compare/trend, apartment: название или null, meter: название или null, resource: COLD_WATER/HOT_WATER/WATER/ELECTRICITY/GAS или null, register: TOTAL/T1/T2/T3 или null, from: дата или null, to: дата или null}. Не подставляй финансовый месяц, если период не назван. Не смешивай плату за воду с потреблением воды. Не меняй показания; запись выполняется вручную в кабинете.")
        appendLine("Каталог квартир и приборов (только названия и ресурс):")
        s.apartments.forEach { a ->
            appendLine(a.name)
            s.meters.filter { it.apartmentId == a.id }.forEach { m -> appendLine("  ${m.name}: ${m.resource}") }
        }
    }
}
