package ru.my.traty.data.update

/**
 * Сравнение номеров версий вида «0.1», «0.1.1», «v0.2». Версии с суффиксом («0.2-test», «1.0-beta») и мусор
 * разобрать нельзя: такие версии никогда не считаются новее, чтобы не тревожить людей пробными сборками.
 */
object AppVersion {
    private val PATTERN = Regex("""^v?(\d{1,6}(?:\.\d{1,6}){0,3})$""")

    /** Числа версии или null, если строка не похожа на обычный номер версии. */
    fun parse(text: String?): List<Int>? {
        val match = PATTERN.matchEntire(text.orEmpty().trim()) ?: return null
        return match.groupValues[1].split('.').map { it.toInt() }
    }

    /** [candidate] новее [current]. Если любую из версий не разобрать, ответ «нет». */
    fun isNewer(candidate: String?, current: String?): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Версия без ведущей «v»: «v0.2» → «0.2». */
    fun clean(text: String?): String = text.orEmpty().trim().removePrefix("v")
}
