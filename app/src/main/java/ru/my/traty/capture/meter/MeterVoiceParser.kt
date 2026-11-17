package ru.my.traty.capture.meter

import java.time.LocalDate

data class MeterVoiceDraft(val valuesMilli: Map<String, Long>, val date: LocalDate?)

/** Только выбранный счётчик. Любая неоднозначность отклоняет всю фразу, не заполняя часть полей. */
object MeterVoiceParser {
    private val tariff = Regex(
        """(?<![\p{L}\d])(?:тариф\s+(?:номер\s+)?|[tт]\s*)(один|первый|1|два|второй|2|три|третий|3)(?![\p{L}\d])""",
    )
    private val codes = mapOf("один" to "T1", "первый" to "T1", "1" to "T1",
        "два" to "T2", "второй" to "T2", "2" to "T2", "три" to "T3", "третий" to "T3", "3" to "T3")
    private val filler = Regex("""(?<!\p{L})(?:показания|показание|запиши|записать|внеси|внести|равно)(?!\p{L})""")

    fun parse(text: String, registerCodes: List<String>, today: LocalDate = LocalDate.now()): MeterVoiceDraft {
        require(registerCodes.isNotEmpty()) { "У счётчика нет тарифов" }
        val dated = SpokenReadingDate.extract(text.lowercase().replace('ё', 'е'), today)
        val cleaned = dated.text.replace(filler, " ").trim()
        val markers = tariff.findAll(cleaned).toList()
        if (markers.isEmpty()) {
            require(registerCodes.size == 1) { "Назовите тариф: например, «тариф один — 5200, тариф два — 1800»" }
            return MeterVoiceDraft(mapOf(registerCodes.single() to number(cleaned)), dated.date)
        }
        require(cleaned.substring(0, markers.first().range.first).trim(' ', ',', ';', ':', '.', '—', '–').isEmpty()) {
            "Не удалось определить, к какому тарифу относится число"
        }
        val values = linkedMapOf<String, Long>()
        markers.forEachIndexed { index, marker ->
            val code = codes.getValue(marker.groupValues[1])
            require(code in registerCodes) { "У выбранного счётчика нет тарифа " + code }
            require(code !in values) { "Тариф " + code + " назван несколько раз. Повторите фразу без исправлений" }
            val end = markers.getOrNull(index + 1)?.range?.first ?: cleaned.length
            val segment = cleaned.substring(marker.range.last + 1, end).replace(Regex("""(?:^|\s)и\s*$"""), "")
            values[code] = number(segment)
        }
        return MeterVoiceDraft(values, dated.date)
    }

    private fun number(text: String): Long =
        SpokenReadingNumber.parse(text.trim().trim(',', ';', ':', '.', '—', '–').trim())
            ?: throw IllegalArgumentException("Не удалось однозначно разобрать число. Назовите показание и до трёх цифр после запятой")
}
