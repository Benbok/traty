package ru.my.traty.data

import androidx.room.withTransaction

/** Ошибка операции с категориями; текст показывается пользователю. */
class CategoryException(message: String) : Exception(message)

data class ResolvedIds(val categoryId: Long, val subcategoryId: Long?)

/** trim, схлопнуть пробелы, не длиннее 40 символов, первая буква заглавная. */
fun normalizeName(raw: String): String =
    raw.trim().replace(Regex("""\s+"""), " ").take(40).trim().replaceFirstChar { it.uppercaseChar() }

/** Сравнение без учёта регистра — в Kotlin: COLLATE NOCASE в SQLite не понимает кириллицу. */
internal fun sameName(a: String, b: String): Boolean = normalizeName(a).lowercase() == normalizeName(b).lowercase()

class CategoryRepository(private val db: AppDatabase) {
    private val dao get() = db.categories()

    suspend fun seedIfEmpty() = db.withTransaction {
        if (dao.count() > 0) return@withTransaction
        DefaultCategories.list.forEach { (cat, subs) ->
            val id = dao.insert(cat)
            subs.forEachIndexed { i, s -> dao.insertSub(Subcategory(categoryId = id, name = s, sortOrder = i)) }
        }
    }

    /**
     * Находит категорию и подкатегорию по названию (без учёта регистра) или создаёт их.
     * Пустая категория → «Прочее». Архивная категория переиспользуется как есть.
     */
    suspend fun resolveForSave(categoryName: String, newCategoryEmoji: String?, subcategoryName: String?): ResolvedIds =
        db.withTransaction {
            val all = dao.getAllWithSubs()
            val name = normalizeName(categoryName).ifEmpty { DefaultCategories.OTHER }
            val found = all.firstOrNull { sameName(it.category.name, name) }
            val categoryId: Long
            val subs: List<Subcategory>
            if (found != null) {
                categoryId = found.category.id
                subs = found.subs
            } else {
                val emoji = newCategoryEmoji?.trim()?.takeIf { it.isNotEmpty() } ?: DefaultCategories.NEW_EMOJI
                val order = (all.maxOfOrNull { it.category.sortOrder } ?: -1) + 1
                categoryId = dao.insert(Category(name = name, emoji = emoji, sortOrder = order))
                subs = emptyList()
            }
            val subName = subcategoryName?.let(::normalizeName)?.ifEmpty { null }
            val subId = subName?.let { sn ->
                subs.firstOrNull { sameName(it.name, sn) }?.id
                    ?: dao.insertSub(
                        Subcategory(categoryId = categoryId, name = sn, sortOrder = (subs.maxOfOrNull { it.sortOrder } ?: -1) + 1)
                    )
            }
            ResolvedIds(categoryId, subId)
        }
}
