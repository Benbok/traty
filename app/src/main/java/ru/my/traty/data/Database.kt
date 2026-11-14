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

/** Откуда пришла трата. */
object ExpenseSource {
    const val VOICE = "voice"
    const val TEXT = "text"
    const val MANUAL = "manual"
    const val IMPORT = "import"
    const val RECONCILE = "reconcile"
}

/** Откуда пришло пополнение. */
object IncomeOrigin {
    const val MANUAL = "manual"
    const val TEXT = "text"
    const val VOICE = "voice"
    const val RECONCILE = "reconcile"
}

/** Записи расхождения правятся и удаляются только через «Переделать». */
val Expense.isUserEditable: Boolean get() = source != ExpenseSource.RECONCILE
val Income.isUserEditable: Boolean get() = origin != IncomeOrigin.RECONCILE

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

    /** Суммы по (категория, место) — для разбивки по магазинам. Написания одного магазина сводит [Places.group]. */
    @Query(
        "SELECT categoryId, place, SUM(amountKopecks) AS total, COUNT(*) AS count FROM expenses " +
            "WHERE timestamp >= :from AND timestamp < :to GROUP BY categoryId, place"
    )
    fun observePlaceTotals(from: Long, to: Long): Flow<List<PlaceTotal>>

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

@Database(
    entities = [Expense::class, Category::class, Subcategory::class, Income::class, Reconciliation::class, Template::class, Rule::class, PendingPhrase::class],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao
    abstract fun categories(): CategoryDao
    abstract fun incomes(): IncomeDao
    abstract fun reconciliations(): ReconciliationDao
    abstract fun templates(): TemplateDao
    abstract fun rules(): RuleDao
    abstract fun pendingPhrases(): PendingPhraseDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `incomes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amountKopecks` INTEGER NOT NULL,
                        `currency` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `note` TEXT,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_incomes_timestamp` ON `incomes` (`timestamp`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `incomes` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reconciliations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `actualKopecks` INTEGER NOT NULL, `expectedKopecks` INTEGER, " +
                        "`differenceKopecks` INTEGER NOT NULL, `expenseId` INTEGER, `incomeId` INTEGER, `isOpening` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliations_timestamp` ON `reconciliations` (`timestamp`)")
                // Сравнение имён без учёта регистра — в Kotlin (sameName); вариант пользователя не трогаем.
                var exists = false
                db.query("SELECT `name` FROM `categories`").use { c ->
                    while (c.moveToNext()) if (sameName(c.getString(0), DefaultCategories.UNACCOUNTED)) exists = true
                }
                if (!exists) {
                    db.execSQL(
                        "INSERT INTO `categories` (`name`, `emoji`, `color`, `sortOrder`, `isArchived`) " +
                            "SELECT '${DefaultCategories.UNACCOUNTED}', '${DefaultCategories.UNACCOUNTED_EMOJI}', NULL, " +
                            "COALESCE((SELECT MAX(`sortOrder`) FROM `categories` WHERE `name` <> '${DefaultCategories.OTHER}'), -1) + 1, 0"
                    )
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amountKopecks` INTEGER NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `subcategoryId` INTEGER,
                        `place` TEXT,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_templates_categoryId` ON `templates` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_templates_subcategoryId` ON `templates` (`subcategoryId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `subcategoryId` INTEGER,
                        `hits` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_categoryId` ON `rules` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_subcategoryId` ON `rules` (`subcategoryId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rules_pattern` ON `rules` (`pattern`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_phrases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `resultJson` TEXT, `error` TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_phrases_status` ON `pending_phrases` (`status`)")
            }
        }

        fun build(context: Context, name: String = "traty.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // Решение владельца: в v1 реальных данных нет — базу v1 пересоздаём.
                // Для версий 2+ — только Migration(n, n+1).
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}

/** Стартовый набор категорий — применяется только к пустой базе. Правятся в Настройки → Категории. */
object DefaultCategories {
    const val OTHER = "Прочее"
    const val UNACCOUNTED = "Неучтённые"
    const val UNACCOUNTED_EMOJI = "🫥"
    const val NEW_EMOJI = "🏷️"

    private fun c(name: String, emoji: String, vararg subs: String) = Category(name = name, emoji = emoji) to subs.toList()

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
