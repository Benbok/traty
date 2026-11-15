package ru.my.traty.data.weekly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyTaskDao {
    @Insert
    suspend fun insertAll(items: List<WeeklyTask>)

    @Insert
    suspend fun insert(item: WeeklyTask): Long

    @Update
    suspend fun update(item: WeeklyTask)

    @Query("SELECT * FROM weekly_tasks WHERE weekStart = :weekStart ORDER BY id")
    suspend fun getWeek(weekStart: Long): List<WeeklyTask>

    @Query("SELECT * FROM weekly_tasks ORDER BY weekStart DESC, id")
    fun observeAll(): Flow<List<WeeklyTask>>

    @Query("SELECT * FROM weekly_tasks ORDER BY weekStart DESC, id")
    suspend fun getAll(): List<WeeklyTask>
}

object WeeklyMigrations {
    /** Задания на неделю. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `weekly_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `weekStart` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `categoryId` INTEGER,
                    `title` TEXT NOT NULL,
                    `target` INTEGER NOT NULL,
                    `usual` INTEGER NOT NULL,
                    `usualKopecks` INTEGER NOT NULL,
                    `custom` INTEGER NOT NULL,
                    `active` INTEGER NOT NULL,
                    `checked` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_weekly_tasks_weekStart` ON `weekly_tasks` (`weekStart`)")
        }
    }
}
