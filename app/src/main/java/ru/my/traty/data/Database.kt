package ru.my.traty.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import ru.my.traty.data.budget.Budget
import ru.my.traty.data.budget.BudgetDao
import ru.my.traty.data.budget.BudgetMigrations
import ru.my.traty.data.budget.MonthBudget
import ru.my.traty.data.budget.MonthBudgetDao
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtDao
import ru.my.traty.data.debt.DebtMigrations
import ru.my.traty.data.debt.DebtPayment
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ExpenseItemDao
import ru.my.traty.data.items.ItemMigrations
import ru.my.traty.data.weekly.WeeklyMigrations
import ru.my.traty.data.weekly.WeeklyTask
import ru.my.traty.data.weekly.WeeklyTaskDao

/** Категория трат. «Прочее» существует всегда и в списках идёт последней. */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** ARGB-цвет или null (цвет по умолчанию) */
    val color: Int? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    /** Здесь бывают импульсивные траты: метка ⚡ ставится только в таких категориях. */
    @ColumnInfo(defaultValue = "0") val isImpulsive: Boolean = false,
)

@Entity(
    tableName = "subcategories",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index(value = ["categoryId", "name"], unique = true)],
)
data class Subcategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int = 0,
)

/** «Неучтённые» не выбираются ни пользователем, ни ИИ для обычных трат. */
fun List<CategoryWithSubs>.selectable(): List<CategoryWithSubs> =
    filterNot { sameName(it.category.name, DefaultCategories.UNACCOUNTED) }

/** Одна трата. Суммы храним в копейках (Long), чтобы не было ошибок округления. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"]),
        ForeignKey(
            entity = Subcategory::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("subcategoryId"), Index("timestamp")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountKopecks: Long,
    val currency: String = "RUB",
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val place: String? = null,
    val note: String? = null,
    /** Время траты, epoch millis */
    val timestamp: Long,
    /** Исходная фраза (голос или текст) — пригодится для отладки разбора */
    val rawText: String? = null,
    val source: String = ExpenseSource.TEXT,
)

data class CategoryWithSubs(
    @Embedded val category: Category,
    @Relation(parentColumn = "id", entityColumn = "categoryId") val subs: List<Subcategory>,
) {
    fun subNames(): List<String> = subs.sortedWith(compareBy({ it.sortOrder }, { it.name })).map { it.name }
}

/** Трата вместе с названиями категории и подкатегории — для списков и экспорта. */
data class ExpenseWithCategory(
    @Embedded val expense: Expense,
    val categoryName: String,
    val categoryEmoji: String,
    val subcategoryName: String?,
)

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val total: Long,
)

private const val EXPENSE_JOIN =
    "SELECT e.*, c.name AS categoryName, c.emoji AS categoryEmoji, s.name AS subcategoryName " +
        "FROM expenses e JOIN categories c ON c.id = e.categoryId " +
        "LEFT JOIN subcategories s ON s.id = e.subcategoryId"

/** «Прочее» всегда последнее */
private const val CATEGORY_ORDER = "ORDER BY (name = 'Прочее'), sortOrder, name"

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(item: Expense): Long

    @Insert
    suspend fun insertAll(items: List<Expense>)

    @Update
    suspend fun update(item: Expense)

    @Delete
    suspend fun delete(item: Expense)

    @Query("$EXPENSE_JOIN WHERE e.timestamp >= :from AND e.timestamp < :to ORDER BY e.timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<ExpenseWithCategory>>

    @Query("$EXPENSE_JOIN WHERE e.timestamp >= :from AND e.timestamp < :to ORDER BY e.timestamp DESC LIMIT :limit")
    suspend fun getRange(from: Long, to: Long, limit: Int = 10): List<ExpenseWithCategory>

    @Query("$EXPENSE_JOIN ORDER BY e.timestamp ASC")
    fun observeAllWithCategory(): Flow<List<ExpenseWithCategory>>

    @Query("SELECT * FROM expenses ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<Expense>>

    @Query(
        "SELECT c.id AS categoryId, c.name AS name, c.emoji AS emoji, SUM(e.amountKopecks) AS total " +
            "FROM expenses e JOIN categories c ON c.id = e.categoryId " +
            "WHERE e.timestamp >= :from AND e.timestamp < :to GROUP BY c.id ORDER BY total DESC"
    )
    fun observeTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    /** Все названия мест, что уже есть в базе: при сохранении новое написание приводится к уже известному. */
    @Query("SELECT DISTINCT place FROM expenses WHERE place IS NOT NULL AND place <> ''")
    suspend fun distinctPlaces(): List<String>

    @Query("$EXPENSE_JOIN ORDER BY e.timestamp")
    suspend fun getAllWithCategory(): List<ExpenseWithCategory>

    @Query("$EXPENSE_JOIN WHERE e.id IN (:ids) ORDER BY e.id")
    suspend fun getByIds(ids: List<Long>): List<ExpenseWithCategory>

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId AND amountKopecks = :amountKopecks " +
            "AND timestamp >= :fromTs AND timestamp <= :toTs"
    )
    suspend fun countNear(categoryId: Long, amountKopecks: Long, fromTs: Long, toTs: Long): Int

    @Query("UPDATE expenses SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun reassignCategory(fromId: Long, toId: Long)

    @Query("UPDATE expenses SET subcategoryId = :toId WHERE subcategoryId = :fromId")
    suspend fun reassignSubcategory(fromId: Long, toId: Long)

    @Query("SELECT COALESCE(SUM(amountKopecks), 0) FROM expenses WHERE timestamp >= :startMillis AND timestamp < :endMillisExclusive")
    suspend fun sumRange(startMillis: Long, endMillisExclusive: Long): Long

    @Query("SELECT COALESCE(SUM(amountKopecks), 0) FROM expenses WHERE timestamp > :afterMillis")
    suspend fun sumAfter(afterMillis: Long): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT MIN(timestamp) FROM expenses")
    suspend fun getFirstTimestamp(): Long?
}

@Dao
interface CategoryDao {
    @Transaction
    @Query("SELECT * FROM categories WHERE isArchived = 0 $CATEGORY_ORDER")
    fun observeActiveWithSubs(): Flow<List<CategoryWithSubs>>

    @Transaction
    @Query("SELECT * FROM categories WHERE isArchived = 0 $CATEGORY_ORDER")
    suspend fun getActiveWithSubs(): List<CategoryWithSubs>

    @Transaction
    @Query("SELECT * FROM categories $CATEGORY_ORDER")
    fun observeAllWithSubs(): Flow<List<CategoryWithSubs>>

    @Transaction
    @Query("SELECT * FROM categories $CATEGORY_ORDER")
    suspend fun getAllWithSubs(): List<CategoryWithSubs>

    @Insert
    suspend fun insert(item: Category): Long

    @Update
    suspend fun update(item: Category)

    @Delete
    suspend fun delete(item: Category)

    @Insert
    suspend fun insertSub(item: Subcategory): Long

    @Update
    suspend fun updateSub(item: Subcategory)

    @Delete
    suspend fun deleteSub(item: Subcategory)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

/** Доход / пополнение (дебет). */
@Entity(tableName = "incomes", indices = [Index("timestamp")])
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountKopecks: Long,
    val currency: String = "RUB",
    val source: String = "Прочее",
    val note: String? = null,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "'manual'") val origin: String = IncomeOrigin.MANUAL,
)

/** Сверка баланса с реальным остатком. */
@Entity(tableName = "reconciliations", indices = [Index("timestamp")])
data class Reconciliation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val actualKopecks: Long,
    val expectedKopecks: Long?,
    val differenceKopecks: Long,
    val expenseId: Long?,
    val incomeId: Long?,
    val isOpening: Boolean,
)

@Dao
interface ReconciliationDao {
    @Insert
    suspend fun insert(item: Reconciliation): Long

    @Delete
    suspend fun delete(item: Reconciliation)

    @Query("SELECT * FROM reconciliations ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLast(): Reconciliation?

    @Query("SELECT * FROM reconciliations ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<Reconciliation>

    @Query("SELECT * FROM reconciliations ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<Reconciliation>>
}

@Dao
interface IncomeDao {
    @Insert
    suspend fun insert(item: Income): Long

    @Insert
    suspend fun insertAll(items: List<Income>)

    @Update
    suspend fun update(item: Income)

    @Delete
    suspend fun delete(item: Income)

    @Query("SELECT * FROM incomes WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<Income>>

    @Query("SELECT * FROM incomes ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<Income>>

    @Query("SELECT * FROM incomes ORDER BY timestamp ASC")
    suspend fun getAll(): List<Income>

    @Query("SELECT * FROM incomes WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun getRange(from: Long, to: Long): List<Income>

    @Query("SELECT SUM(amountKopecks) FROM incomes WHERE timestamp >= :from AND timestamp < :to")
    fun observeTotal(from: Long, to: Long): Flow<Long?>

    @Query("SELECT COALESCE(SUM(amountKopecks), 0) FROM incomes WHERE timestamp > :afterMillis")
    suspend fun sumAfter(afterMillis: Long): Long

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM incomes WHERE id IN (:ids) ORDER BY id")
    suspend fun getByIds(ids: List<Long>): List<Income>

    /** Для поиска дублей при импорте CSV: пополнения на ту же сумму в окне по времени. */
    @Query("SELECT COUNT(*) FROM incomes WHERE amountKopecks = :amountKopecks AND timestamp >= :fromTs AND timestamp <= :toTs")
    suspend fun countNear(amountKopecks: Long, fromTs: Long, toTs: Long): Int

    @Query("SELECT MIN(timestamp) FROM incomes")
    suspend fun getFirstTimestamp(): Long?
}

/** Шаблон быстрой траты — чипс над строкой ввода. */
@Entity(
    tableName = "templates",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"]),
        ForeignKey(
            entity = Subcategory::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("subcategoryId")],
)
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountKopecks: Long,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val place: String? = null,
    val sortOrder: Int = 0,
)

/** Шаблон вместе с названиями категории и подкатегории — для отображения. */
data class TemplateWithCategory(
    @Embedded val template: Template,
    val categoryName: String,
    val categoryEmoji: String,
    val subcategoryName: String?,
)

private const val TEMPLATE_JOIN =
    "SELECT t.*, c.name AS categoryName, c.emoji AS categoryEmoji, s.name AS subcategoryName " +
        "FROM templates t JOIN categories c ON c.id = t.categoryId " +
        "LEFT JOIN subcategories s ON s.id = t.subcategoryId"

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(item: Template): Long

    @Update
    suspend fun update(item: Template)

    @Delete
    suspend fun delete(item: Template)

    @Query("$TEMPLATE_JOIN ORDER BY t.sortOrder, t.title")
    fun observeAll(): Flow<List<TemplateWithCategory>>

    @Query("$TEMPLATE_JOIN ORDER BY t.sortOrder, t.title")
    suspend fun getAll(): List<TemplateWithCategory>
}

/** Правило «шаблон/место → категория». Применяется до ИИ при разборе трат. */
@Entity(
    tableName = "rules",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"]),
        ForeignKey(
            entity = Subcategory::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("subcategoryId"), Index(value = ["pattern"], unique = true)],
)
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Текст или место, к которому привязано правило (lowercase). */
    val pattern: String,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    /** Сколько раз правило сработало — для статистики. */
    val hits: Int = 0,
)

data class RuleWithNames(
    @Embedded val rule: Rule,
    val categoryName: String,
    val categoryEmoji: String,
    val subcategoryName: String?,
)

private const val RULE_JOIN =
    "SELECT r.*, c.name AS categoryName, c.emoji AS categoryEmoji, s.name AS subcategoryName " +
        "FROM rules r JOIN categories c ON c.id = r.categoryId " +
        "LEFT JOIN subcategories s ON s.id = r.subcategoryId"

@Dao
interface RuleDao {
    @Insert
    suspend fun insert(item: Rule): Long

    @Update
    suspend fun update(item: Rule)

    @Delete
    suspend fun delete(item: Rule)

    @Query("$RULE_JOIN ORDER BY r.hits DESC, r.pattern")
    fun observeAll(): Flow<List<RuleWithNames>>

    @Query("SELECT * FROM rules ORDER BY hits DESC")
    suspend fun getAll(): List<Rule>

    @Query("SELECT * FROM rules WHERE pattern = :pattern LIMIT 1")
    suspend fun findByPattern(pattern: String): Rule?

    @Query("UPDATE rules SET hits = hits + 1 WHERE id = :id")
    suspend fun incrementHits(id: Long)
}

/** Состояния регулярного платежа. DISMISSED — «это не подписка»: хранится, чтобы не предлагать снова. */
object RecurringState {
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val DISMISSED = "dismissed"
}

/**
 * Регулярный платёж (подписка, аренда, кредит). Период — «раз в [periodEvery] [periodUnit]»,
 * [nextEpochDay] — дата следующего списания (LocalDate.toEpochDay, без часового пояса).
 */
@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"]),
        ForeignKey(
            entity = Subcategory::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("subcategoryId"), Index("detectKey")],
)
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountKopecks: Long,
    val currency: String = "RUB",
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val place: String? = null,
    /** [ru.my.traty.data.recurring.PeriodUnit.key]. */
    val periodUnit: String,
    val periodEvery: Int = 1,
    val nextEpochDay: Long,
    /** С какого дня до списания напоминать (каждый день до самого списания); 0 — не напоминать. */
    val remindDaysBefore: Int = 3,
    val state: String = RecurringState.ACTIVE,
    val autoDetected: Boolean = false,
    /** Ключ автообнаружения ([ru.my.traty.data.recurring.RecurringDetector.keyOf]); у платежей, заведённых вручную, может быть пустым. */
    val detectKey: String? = null,
    /** Сумма каждый раз другая (коммуналка): [amountKopecks] — последняя, ориентировочная. */
    @ColumnInfo(defaultValue = "0") val amountVaries: Boolean = false,
)

data class RecurringWithNames(
    @Embedded val recurring: Recurring,
    val categoryName: String,
    val categoryEmoji: String,
    val subcategoryName: String?,
)

private const val RECURRING_JOIN =
    "SELECT r.*, c.name AS categoryName, c.emoji AS categoryEmoji, s.name AS subcategoryName " +
        "FROM recurring r JOIN categories c ON c.id = r.categoryId " +
        "LEFT JOIN subcategories s ON s.id = r.subcategoryId"

@Dao
interface RecurringDao {
    @Insert
    suspend fun insert(item: Recurring): Long

    @Update
    suspend fun update(item: Recurring)

    @Delete
    suspend fun delete(item: Recurring)

    /** Заведённые платежи (активные и на паузе), ближайшее списание — первым. Отклонённые предложения не показываем. */
    @Query("$RECURRING_JOIN WHERE r.state <> '${RecurringState.DISMISSED}' ORDER BY r.nextEpochDay, r.title")
    fun observeVisible(): Flow<List<RecurringWithNames>>

    /** Все строки, включая отклонённые предложения: по ним автообнаружение не предлагает то же самое снова. */
    @Query("SELECT * FROM recurring")
    suspend fun getAll(): List<Recurring>

    @Query("SELECT * FROM recurring")
    fun observeAll(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun getById(id: Long): Recurring?

    /** При объединении категорий платежи переезжают вместе с тратами. */
    @Query("UPDATE recurring SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun reassignCategory(fromId: Long, toId: Long)

    @Query("UPDATE recurring SET subcategoryId = :toId WHERE subcategoryId = :fromId")
    suspend fun reassignSubcategory(fromId: Long, toId: Long)
}

/** Статусы фраз в очереди без сети. */
object PendingStatus {
    const val WAITING = "waiting"
    const val PARSED = "parsed"
}

/** Распознанная голосом фраза, которая ждёт разбора ИИ (нет сети). [createdAt] — когда фраза произнесена. */
@Entity(tableName = "pending_phrases", indices = [Index("status")])
data class PendingPhrase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val status: String = PendingStatus.WAITING,
    /** JSON черновика (EntryDraft) для статуса PARSED. */
    val resultJson: String? = null,
    val error: String? = null,
)

@Dao
interface PendingPhraseDao {
    @Insert
    suspend fun insert(p: PendingPhrase): Long

    @Update
    suspend fun update(p: PendingPhrase)

    @Query("DELETE FROM pending_phrases WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM pending_phrases WHERE status = :status ORDER BY createdAt")
    suspend fun getByStatus(status: String): List<PendingPhrase>

    @Query("SELECT * FROM pending_phrases ORDER BY createdAt")
    fun observeAll(): Flow<List<PendingPhrase>>
}

/** Стартовый набор категорий — применяется только к пустой базе. Правятся в Настройки → Категории. */
object DefaultCategories {
    const val OTHER = "Прочее"
    const val UNACCOUNTED = "Неучтённые"
    const val UNACCOUNTED_EMOJI = "🫥"
    const val NEW_EMOJI = "🏷️"

    /** Категории, где по умолчанию бывают импульсивные траты (флажок можно сменить в настройках категории). */
    val IMPULSIVE = listOf("Кафе и рестораны", "Доставка еды", "Развлечения", "Одежда и обувь")

    private fun c(name: String, emoji: String, vararg subs: String) =
        Category(name = name, emoji = emoji, isImpulsive = name in IMPULSIVE) to subs.toList()

    val list: List<Pair<Category, List<String>>> = listOf(
        c("Продукты", "🛒", "Супермаркет", "Рынок", "Пекарня"),
        c("Кафе и рестораны", "☕", "Кофе", "Обед", "Фастфуд"),
        c("Доставка еды", "🛵"),
        c("Транспорт", "🚕", "Такси", "Общественный транспорт", "Поезд"),
        c("Авто", "🚗", "Бензин", "Мойка", "ТО", "Парковка", "Страховка", "Штрафы"),
        c("Жильё", "🏠", "Аренда", "Ипотека", "Ремонт"),
        c("Коммунальные услуги", "💡", "Электричество", "Вода", "Отопление"),
        c("Связь и подписки", "📱", "Мобильная связь", "Интернет", "Подписки", "ИИ-сервисы"),
        c("Здоровье", "💊", "Аптека", "Врачи", "Анализы", "Стоматология"),
        c("Спорт", "🏋️", "Абонемент", "Экипировка"),
        c("Красота и уход", "💇", "Парикмахерская", "Косметика"),
        c("Образование", "📚", "Курсы", "Книги", "Конференции", "НМО"),
        c("Одежда и обувь", "👕", "Одежда", "Обувь", "Аксессуары"),
        c("Дом и быт", "🧴", "Бытовая химия", "Техника", "Мебель"),
        c("Развлечения", "🎬", "Кино", "Игры", "Концерты"),
        c("Хобби", "🎨"),
        c("Дети", "🧸", "Игрушки", "Кружки", "Одежда"),
        c("Питомцы", "🐾", "Корм", "Ветеринар"),
        c("Подарки", "🎁"),
        c("Путешествия", "✈️", "Билеты", "Жильё в поездке", "Экскурсии"),
        c("Долги и кредиты", "💳", "Кредит", "Рассрочка"),
        c("Налоги и сборы", "🧾", "Налоги", "Госпошлины"),
        c("Комиссии банков", "🏦"),
        c("Благотворительность", "🤝"),
        c(UNACCOUNTED, UNACCOUNTED_EMOJI),
        c(OTHER, "📦"),
    ).mapIndexed { i, (cat, subs) -> cat.copy(sortOrder = i) to subs }
}
