package ru.my.traty.data

import android.content.Context
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
}

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

    @Query("$EXPENSE_JOIN ORDER BY e.timestamp")
    suspend fun getAllWithCategory(): List<ExpenseWithCategory>

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
)

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
}

@Database(
    entities = [Expense::class, Category::class, Subcategory::class, Income::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao
    abstract fun categories(): CategoryDao
    abstract fun incomes(): IncomeDao

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

        fun build(context: Context, name: String = "traty.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3)
                // Решение владельца: в v1 реальных данных нет — базу v1 пересоздаём.
                // Для версий 2+ — только Migration(n, n+1).
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}

/** Стартовый набор категорий — применяется только к пустой базе. Правятся в Настройки → Категории. */
object DefaultCategories {
    const val OTHER = "Прочее"
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
        c(OTHER, "📦"),
    ).mapIndexed { i, (cat, subs) -> cat.copy(sortOrder = i) to subs }
}
