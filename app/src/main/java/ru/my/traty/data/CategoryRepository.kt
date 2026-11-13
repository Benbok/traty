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

    // --- Редактор категорий ---

    suspend fun addCategory(name: String, emoji: String): Long = db.withTransaction {
        val all = dao.getAllWithSubs()
        val n = validateName(name, all, exceptId = null)
        dao.insert(
            Category(
                name = n,
                emoji = emoji.trim().ifEmpty { DefaultCategories.NEW_EMOJI },
                sortOrder = (all.maxOfOrNull { it.category.sortOrder } ?: -1) + 1,
            )
        )
    }

    suspend fun updateCategory(id: Long, name: String, emoji: String, color: Int?) = db.withTransaction {
        val all = dao.getAllWithSubs()
        val cur = find(all, id)
        val n = if (isOther(cur)) {
            if (!sameName(name, cur.name)) throw CategoryException("«${DefaultCategories.OTHER}» нельзя переименовать")
            cur.name
        } else {
            validateName(name, all, exceptId = id)
        }
        dao.update(cur.copy(name = n, emoji = emoji.trim().ifEmpty { cur.emoji }, color = color))
    }

    suspend fun setArchived(id: Long, archived: Boolean) = db.withTransaction {
        val cur = find(dao.getAllWithSubs(), id)
        if (archived && isOther(cur)) throw CategoryException("«${DefaultCategories.OTHER}» нельзя убрать в архив")
        dao.update(cur.copy(isArchived = archived))
    }

    /** Меняет местами с соседом среди активных; «Прочее» не двигается. */
    suspend fun move(id: Long, up: Boolean) = db.withTransaction {
        val list = dao.getActiveWithSubs().map { it.category }.filterNot(::isOther).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return@withTransaction
        list[i] = list[j].also { list[j] = list[i] }
        list.forEachIndexed { idx, c -> if (c.sortOrder != idx) dao.update(c.copy(sortOrder = idx)) }
    }

    /** Переносит траты и подкатегории в [intoId] и удаляет [fromId]. Возвращает число перенесённых трат. */
    suspend fun merge(fromId: Long, intoId: Long): Int = db.withTransaction {
        if (fromId == intoId) throw CategoryException("Нельзя объединить категорию саму с собой")
        val all = dao.getAllWithSubs()
        val from = all.firstOrNull { it.category.id == fromId } ?: throw CategoryException("Категория не найдена")
        val into = all.firstOrNull { it.category.id == intoId } ?: throw CategoryException("Категория не найдена")
        if (isOther(from.category)) throw CategoryException("«${DefaultCategories.OTHER}» нельзя объединить с другой категорией")
        val moved = db.expenses().countByCategory(fromId)
        var nextOrder = (into.subs.maxOfOrNull { it.sortOrder } ?: -1) + 1
        from.subs.forEach { sub ->
            val same = into.subs.firstOrNull { sameName(it.name, sub.name) }
            if (same != null) {
                db.expenses().reassignSubcategory(sub.id, same.id)
                dao.deleteSub(sub)
            } else {
                dao.updateSub(sub.copy(categoryId = intoId, sortOrder = nextOrder++))
            }
        }
        db.expenses().reassignCategory(fromId, intoId)
        dao.delete(from.category)
        moved
    }

    suspend fun addSubcategory(categoryId: Long, name: String): Long = db.withTransaction {
        val cat = dao.getAllWithSubs().firstOrNull { it.category.id == categoryId }
            ?: throw CategoryException("Категория не найдена")
        val n = validateSubName(name, cat, exceptId = null)
        dao.insertSub(Subcategory(categoryId = categoryId, name = n, sortOrder = (cat.subs.maxOfOrNull { it.sortOrder } ?: -1) + 1))
    }

    suspend fun renameSubcategory(id: Long, name: String) = db.withTransaction {
        val (cat, sub) = findSub(id)
        dao.updateSub(sub.copy(name = validateSubName(name, cat, exceptId = id)))
    }

    /** Траты остаются в категории, ссылка на подкатегорию обнуляется (ON DELETE SET NULL). */
    suspend fun deleteSubcategory(id: Long) = db.withTransaction {
        dao.deleteSub(findSub(id).second)
    }

    // --- Вспомогательное ---

    private fun isOther(c: Category) = c.name == DefaultCategories.OTHER

    private fun find(all: List<CategoryWithSubs>, id: Long): Category =
        all.firstOrNull { it.category.id == id }?.category ?: throw CategoryException("Категория не найдена")

    private suspend fun findSub(id: Long): Pair<CategoryWithSubs, Subcategory> {
        for (c in dao.getAllWithSubs()) c.subs.firstOrNull { it.id == id }?.let { return c to it }
        throw CategoryException("Подкатегория не найдена")
    }

    private fun validateName(raw: String, all: List<CategoryWithSubs>, exceptId: Long?): String {
        val n = normalizeName(raw)
        if (n.isEmpty()) throw CategoryException("Введите название")
        all.firstOrNull { it.category.id != exceptId && sameName(it.category.name, n) }
            ?.let { throw CategoryException("Категория «${it.category.name}» уже есть") }
        return n
    }

    private fun validateSubName(raw: String, cat: CategoryWithSubs, exceptId: Long?): String {
        val n = normalizeName(raw)
        if (n.isEmpty()) throw CategoryException("Введите название")
        cat.subs.firstOrNull { it.id != exceptId && sameName(it.name, n) }
            ?.let { throw CategoryException("Подкатегория «${it.name}» уже есть") }
        return n
    }
}
