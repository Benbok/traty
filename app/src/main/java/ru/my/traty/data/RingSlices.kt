package ru.my.traty.data

/** Доля кольцевой диаграммы. [categoryId] == null — «Остальное» (все мелкие категории вместе). */
data class RingSlice(val categoryId: Long?, val name: String, val emoji: String, val total: Long, val fraction: Float)

object RingSlices {
    const val REST = "Остальное"

    /**
     * Самые крупные [maxSlices] категорий, остальное — одной долей. Если «остальное» — ровно одна категория,
     * она показывается сама, без объединения. Пустые и нулевые категории пропускаются.
     */
    fun of(totals: List<CategoryTotal>, maxSlices: Int = 5): List<RingSlice> {
        val positive = totals.filter { it.total > 0 }.sortedByDescending { it.total }
        val sum = positive.sumOf { it.total }
        if (sum == 0L) return emptyList()
        fun frac(v: Long) = v.toFloat() / sum
        val head = if (positive.size <= maxSlices + 1) positive else positive.take(maxSlices)
        val slices = head.map { RingSlice(it.categoryId, it.name, it.emoji, it.total, frac(it.total)) }
        val rest = positive.drop(head.size).sumOf { it.total }
        return if (rest > 0) slices + RingSlice(null, REST, "", rest, frac(rest)) else slices
    }
}
