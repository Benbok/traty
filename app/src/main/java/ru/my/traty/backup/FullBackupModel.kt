package ru.my.traty.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.my.traty.data.PortableSettings

/** Версия формата файла полной копии. Поднимать, когда меняется смысл полей, а не при добавлении новых. */
const val FULL_BACKUP_FORMAT_VERSION = 1

/** Пишется в каждый файл: человек, открывший копию, сразу видит, чего в ней нет. */
const val FULL_BACKUP_NOTICE =
    "Полная резервная копия «Трат». Ключи ИИ (DeepSeek, YandexGPT) и ID каталога Yandex Cloud в копию не входят, " +
        "модель распознавания речи тоже: на новом телефоне ключи вводятся заново, модель скачивается заново."

/**
 * Полная резервная копия: один JSON-файл в UTF-8.
 * Деньги в копейках (Long), время в epoch millis, дни — epoch day, как в базе.
 * Связи записаны по названиям (категория по имени, подкатегория по паре «категория, имя»)
 * и по локальным ключам [BackupExpense.key], [BackupIncome.key], [BackupDebt.key] внутри файла —
 * копия не зависит от id записей на старом телефоне.
 */
@Serializable
data class FullBackup(
    val formatVersion: Int,
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersion: String = "",
    val notice: String = FULL_BACKUP_NOTICE,
    val categories: List<BackupCategory> = emptyList(),
    val subcategories: List<BackupSubcategory> = emptyList(),
    val expenses: List<BackupExpense> = emptyList(),
    @SerialName("expense_items") val expenseItems: List<BackupExpenseItem> = emptyList(),
    val incomes: List<BackupIncome> = emptyList(),
    val reconciliations: List<BackupReconciliation> = emptyList(),
    val rules: List<BackupRule> = emptyList(),
    val templates: List<BackupTemplate> = emptyList(),
    val recurring: List<BackupRecurring> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    @SerialName("month_budgets") val monthBudgets: List<BackupMonthBudget> = emptyList(),
    @SerialName("weekly_tasks") val weeklyTasks: List<BackupWeeklyTask> = emptyList(),
    val debts: List<BackupDebt> = emptyList(),
    @SerialName("debt_payments") val debtPayments: List<BackupDebtPayment> = emptyList(),
    @SerialName("pending_phrases") val pendingPhrases: List<BackupPendingPhrase> = emptyList(),
    val settings: PortableSettings = PortableSettings(),
)

@Serializable
data class BackupCategory(
    val name: String,
    val emoji: String,
    val color: Int? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val isImpulsive: Boolean = false,
)

@Serializable
data class BackupSubcategory(val category: String, val name: String, val sortOrder: Int = 0)

@Serializable
data class BackupExpense(
    val key: Long,
    val amountKopecks: Long,
    val currency: String = "RUB",
    val category: String,
    val subcategory: String? = null,
    val place: String? = null,
    val note: String? = null,
    val timestamp: Long,
    val rawText: String? = null,
    val source: String,
)

/** Позиция чека; [expense] — [BackupExpense.key] её траты. */
@Serializable
data class BackupExpenseItem(
    val expense: Long,
    val position: Int = 0,
    val name: String,
    val priceKopecks: Long? = null,
    val quantityMilli: Long? = null,
    val unit: String? = null,
)

@Serializable
data class BackupIncome(
    val key: Long,
    val amountKopecks: Long,
    val currency: String = "RUB",
    val source: String,
    val note: String? = null,
    val timestamp: Long,
    val origin: String,
)

/** [expense] и [income] — ключи записи расхождения в этом файле; null — её нет. */
@Serializable
data class BackupReconciliation(
    val timestamp: Long,
    val actualKopecks: Long,
    val expectedKopecks: Long? = null,
    val differenceKopecks: Long,
    val expense: Long? = null,
    val income: Long? = null,
    val isOpening: Boolean = false,
)

@Serializable
data class BackupRule(val pattern: String, val category: String, val subcategory: String? = null, val hits: Int = 0)

@Serializable
data class BackupTemplate(
    val title: String,
    val amountKopecks: Long,
    val category: String,
    val subcategory: String? = null,
    val place: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupRecurring(
    val title: String,
    val amountKopecks: Long,
    val currency: String = "RUB",
    val category: String,
    val subcategory: String? = null,
    val place: String? = null,
    val periodUnit: String,
    val periodEvery: Int = 1,
    val nextEpochDay: Long,
    val remindDaysBefore: Int = 3,
    val state: String,
    val autoDetected: Boolean = false,
    val detectKey: String? = null,
    val amountVaries: Boolean = false,
)

@Serializable
data class BackupBudget(
    val category: String,
    val subcategory: String? = null,
    val month: Int,
    val amountKopecks: Long,
    val repeats: Boolean = true,
)

@Serializable
data class BackupMonthBudget(val month: Int, val amountKopecks: Long, val repeats: Boolean = true)

/** [category] — имя категории; null — задание без категории или её уже удалили. */
@Serializable
data class BackupWeeklyTask(
    val weekStart: Long,
    val type: String,
    val category: String? = null,
    val title: String,
    val target: Long,
    val usual: Long = 0,
    val usualKopecks: Long = 0,
    val custom: Boolean = false,
    val active: Boolean = true,
    val checked: Boolean = false,
    val createdAt: Long = 0,
)

/** [category] — null, если категорию покупки уже удалили: при восстановлении покупка попадёт в «Прочее». */
@Serializable
data class BackupDebt(
    val key: Long,
    val title: String,
    val place: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val kind: String,
    val direction: String,
    val currency: String = "RUB",
    val totalKopecks: Long? = null,
    val rateBasisPoints: Int = 0,
    val paymentsCount: Int? = null,
    val periodUnit: String,
    val periodEvery: Int = 1,
    val firstDueEpochDay: Long? = null,
    val paymentKopecks: Long? = null,
    val state: String,
    val createdAt: Long = 0,
)

/** Платёж по покупке: [debt] — [BackupDebt.key], [expense] — [BackupExpense.key]. */
@Serializable
data class BackupDebtPayment(val debt: Long, val expense: Long, val part: String)

/**
 * Неразобранная фраза. Переносится только текст и время: черновик разбора ссылается на записи
 * старого телефона, поэтому на новом фраза разбирается заново.
 */
@Serializable
data class BackupPendingPhrase(val text: String, val createdAt: Long)

/** Сколько записей в копии, по-русски для окна подтверждения; разделы без записей не показываются. */
fun FullBackup.summary(): List<Pair<String, Int>> = listOf(
    "Категории" to categories.size,
    "Подкатегории" to subcategories.size,
    "Траты" to expenses.size,
    "Товары в покупках" to expenseItems.size,
    "Пополнения" to incomes.size,
    "Сверки баланса" to reconciliations.size,
    "Правила категорий" to rules.size,
    "Шаблоны" to templates.size,
    "Подписки и регулярные платежи" to recurring.size,
    "Планы трат" to budgets.size + monthBudgets.size,
    "Задания на неделю" to weeklyTasks.size,
    "Покупки частями и кредиты" to debts.size,
    "Неразобранные фразы" to pendingPhrases.size,
).filter { it.second > 0 }

/** Записей всего, без категорий: для сообщений «сохранено N записей». */
val FullBackup.recordCount: Int
    get() = expenses.size + incomes.size + reconciliations.size + rules.size + templates.size + recurring.size +
        budgets.size + monthBudgets.size + weeklyTasks.size + debts.size + pendingPhrases.size

/** Копию нельзя прочитать или восстановить. [message] — готовый текст для пользователя. */
class FullBackupException(message: String) : Exception(message)
