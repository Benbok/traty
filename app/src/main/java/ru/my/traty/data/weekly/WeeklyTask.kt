package ru.my.traty.data.weekly

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Вид задания на неделю. Хранится в базе строкой [name]. */
enum class WeeklyTaskType {
    /** Потратить в категории не больше [WeeklyTask.target] копеек. */
    CATEGORY_LIMIT,

    /** Не меньше [WeeklyTask.target] дней недели без трат в категории. */
    NO_SPEND_DAYS,

    /** Не больше [WeeklyTask.target] трат в категории. */
    COUNT_LIMIT,

    /** Пересмотреть подписки; отмечается вручную. */
    REVIEW_SUBSCRIPTIONS,
    ;

    companion object {
        fun of(raw: String): WeeklyTaskType? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Задание на неделю (этап 8). Прогресс не хранится: он каждый раз считается по тратам недели
 * ([WeeklyProgress]), поэтому правка или удаление траты сразу меняет итог.
 *
 * [weekStart] — понедельник недели (epoch day). [target] и [usual] в единицах вида задания:
 * копейки для лимита, дни для «дней без трат», штуки для числа трат и подписок.
 * [usualKopecks] — обычная трата в категории за неделю, из неё считается «сэкономлено».
 * Категорию не связываем внешним ключом: если её удалят, задание останется в истории.
 */
@Entity(tableName = "weekly_tasks", indices = [Index("weekStart")])
data class WeeklyTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,
    val type: String,
    val categoryId: Long? = null,
    val title: String,
    val target: Long,
    val usual: Long = 0,
    val usualKopecks: Long = 0,
    /** Добавлено пользователем, а не предложено приложением. */
    val custom: Boolean = false,
    /** false — задание заменили или убрали; в итогах не участвует. */
    val active: Boolean = true,
    /** Для заданий, которые отмечаются вручную (подписки). */
    val checked: Boolean = false,
    val createdAt: Long = 0,
)
