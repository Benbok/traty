package ru.my.traty.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CategoryRepository(db)
        repo.seedIfEmpty()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun cat(name: String): CategoryWithSubs =
        db.categories().getAllWithSubs().single { it.category.name == name }

    private suspend fun categoryCount() = db.categories().getAllWithSubs().size

    @Test
    fun resolvesExistingCategoryAndSubcategory() = runTest {
        val before = categoryCount()
        val ids = repo.resolveForSave("Продукты", null, "Рынок")
        val products = cat("Продукты")
        assertEquals(products.category.id, ids.categoryId)
        assertEquals(products.subs.single { it.name == "Рынок" }.id, ids.subcategoryId)
        assertEquals(before, categoryCount())
    }

    @Test
    fun resolveIsCaseInsensitiveForCyrillic() = runTest {
        val expected = repo.resolveForSave("Продукты", null, "Рынок")
        val before = categoryCount()
        val subsBefore = cat("Продукты").subs.size
        val ids = repo.resolveForSave("  продукты ", null, "РЫНОК")
        assertEquals(expected, ids)
        assertEquals(before, categoryCount())
        assertEquals(subsBefore, cat("Продукты").subs.size)
    }

    @Test
    fun createsNewCategoryWithSuggestedEmoji() = runTest {
        val ids = repo.resolveForSave("питомцы домашние", "🐶", "Корм")
        val created = cat("Питомцы домашние")
        assertEquals(created.category.id, ids.categoryId)
        assertEquals("🐶", created.category.emoji)
        assertEquals(listOf("Корм"), created.subNames())
        assertEquals(ids.subcategoryId, created.subs.single().id)
    }

    @Test
    fun newCategoryWithoutEmojiGetsTag() = runTest {
        val ids = repo.resolveForSave("Яхты", null, null)
        assertEquals(DefaultCategories.NEW_EMOJI, cat("Яхты").category.emoji)
        assertNull(ids.subcategoryId)
    }

    @Test
    fun blankCategoryFallsBackToOther() = runTest {
        val ids = repo.resolveForSave("", null, null)
        assertEquals(cat(DefaultCategories.OTHER).category.id, ids.categoryId)
    }

    @Test
    fun createsUnknownSubcategoryInExistingCategory() = runTest {
        val ids = repo.resolveForSave("Продукты", null, "Сыры")
        val sub = cat("Продукты").subs.single { it.name == "Сыры" }
        assertEquals(sub.id, ids.subcategoryId)
    }

    @Test
    fun seedCreates25CategoriesWithOtherLast() = runTest {
        val active = db.categories().getActiveWithSubs()
        assertEquals(25, active.size)
        assertEquals(DefaultCategories.OTHER, active.last().category.name)
    }
}
