package ru.my.traty.capture

import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.Places
import kotlin.math.min

/**
 * Распознавание речи путает буквы в названиях: «Пятёрочко», «Магнид». Если название почти совпало с местом, которое
 * уже есть в базе (отличается одной-двумя буквами), берётся привычное написание. Места в ИИ не отправляются:
 * сверка идёт на телефоне, после ответа модели.
 */
object PlaceSnap {
    private const val MIN_LENGTH = 5
    private const val LONG_NAME = 9

    fun apply(entries: List<ParsedEntry>, known: Collection<String>): List<ParsedEntry> =
        if (known.isEmpty()) entries else entries.map { e -> snap(e.place, known)?.let { e.copy(place = it) } ?: e }

    /** Привычное написание вместо опечатки или null, если менять нечего. Подходит только единственное близкое место. */
    fun snap(place: String?, known: Collection<String>): String? {
        val key = Places.key(place)
        if (key.length < MIN_LENGTH) return null
        val byKey = known.filter { it.isNotBlank() }.groupBy { Places.key(it) }
        if (key in byKey) return null
        val limit = if (key.length >= LONG_NAME) 2 else 1
        val near = byKey.keys.filter { it.length >= MIN_LENGTH && distance(key, it, limit) <= limit }
        val only = near.singleOrNull() ?: return null
        return byKey.getValue(only).first()
    }

    /** Расстояние Левенштейна; больше [limit] не считается точно. */
    internal fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
