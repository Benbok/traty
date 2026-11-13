package ru.my.traty.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Одна трата. Суммы храним в копейках (Long), чтобы не было ошибок округления. */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountKopecks: Long,
    val currency: String = "RUB",
    val category: String,
    val subcategory: String? = null,
    val place: String? = null,
    val note: String? = null,
    /** Время траты, epoch millis */
    val timestamp: Long,
    /** Исходная фраза (голос или текст) — пригодится для отладки разбора */
    val rawText: String? = null,
)

/** Категория. Подкатегории — строкой через запятую (для личного приложения этого достаточно). */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    val emoji: String,
    val subcategories: String = "",
    val sortOrder: Int = 0,
) {
    fun subList(): List<String> =
        subcategories.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class CategoryTotal(val category: String, val total: Long)

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insertAll(items: List<Expense>)

    @Update
    suspend fun update(item: Expense)

    @Delete
    suspend fun delete(item: Expense)

    @Query("SELECT * FROM expenses WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<Expense>>

    @Query(
        "SELECT category, SUM(amountKopecks) AS total FROM expenses " +
            "WHERE timestamp >= :from AND timestamp < :to GROUP BY category ORDER BY total DESC"
    )
    fun observeTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM expenses ORDER BY timestamp")
    suspend fun getAll(): List<Expense>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun getAll(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<Category>)

    /** Добавляет только отсутствующие категории, существующие не трогает. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(items: List<Category>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Database(entities = [Expense::class, Category::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao
    abstract fun categories(): CategoryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "traty.db").build()
    }
}

/** Стартовый набор категорий. Меняйте под себя — применяется только к пустой базе. */
object DefaultCategories {
    const val OTHER = "Прочее"

    val list = listOf(
        Category("Продукты", "🛒", "Супермаркет, Рынок, Пекарня", 0),
        Category("Кафе и рестораны", "☕", "Кофе, Обед, Доставка еды, Фастфуд", 1),
        Category("Транспорт", "🚕", "Такси, Общественный транспорт, Поезд", 2),
        Category("Авто", "🚗", "Бензин, Мойка, ТО, Парковка, Страховка", 3),
        Category("Жильё", "🏠", "Аренда, Коммуналка, Ремонт", 4),
        Category("Связь и подписки", "📱", "Мобильная связь, Интернет, Подписки, ИИ-сервисы", 5),
        Category("Здоровье", "💊", "Аптека, Врачи, Анализы, Спорт", 6),
        Category("Образование", "📚", "Курсы, Книги, Конференции, НМО", 7),
        Category("Одежда", "👕", "Одежда, Обувь", 8),
        Category("Дом и быт", "🧴", "Бытовая химия, Техника, Мебель", 9),
        Category("Развлечения", "🎬", "Кино, Игры, Хобби", 10),
        Category("Подарки", "🎁", "", 11),
        Category("Путешествия", "✈️", "Билеты, Жильё в поездке", 12),
        Category(OTHER, "📦", "", 99),
    )
}
