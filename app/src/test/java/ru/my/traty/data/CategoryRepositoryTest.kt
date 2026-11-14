package ru.my.traty.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    private suspend fun assertCategoryError(block: suspend () -> Unit) {
        try {
            block()
            fail("Ожидалась CategoryException")
        } catch (_: CategoryException) {
        }
    }

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
    fun seedCreates26CategoriesWithOtherLast() = runTest {
        val active = db.categories().getActiveWithSubs()
        assertEquals(26, active.size)
        assertEquals(DefaultCategories.OTHER, active.last().category.name)
    }

    // --- Редактор категорий ---

    private suspend fun addExpense(categoryId: Long, subcategoryId: Long? = null, amount: Long = 100): Expense {
        val e = Expense(amountKopecks = amount, categoryId = categoryId, subcategoryId = subcategoryId, timestamp = 1000)
        db.expenses().insertAll(listOf(e))
        return db.expenses().getAllWithCategory().last().expense
    }

    private suspend fun expensesOf(categoryId: Long) =
        db.expenses().getAllWithCategory().filter { it.expense.categoryId == categoryId }

    @Test
    fun renameRejectsDuplicateIgnoringCase() = runTest {
        val hobby = cat("Хобби").category
        assertCategoryError { repo.updateCategory(hobby.id, "продукты", hobby.emoji, null) }
        assertEquals("Хобби", db.categories().getAllWithSubs().single { it.category.id == hobby.id }.category.name)
    }

    @Test
    fun impulsiveFlagIsSeededAndEditable() = runTest {
        val flagged = db.categories().getAllWithSubs().filter { it.category.isImpulsive }.map { it.category.name }.toSet()
        assertEquals(DefaultCategories.IMPULSIVE.toSet(), flagged)
        val hobby = cat("Хобби").category
        assertEquals(false, hobby.isImpulsive)
        repo.updateCategory(hobby.id, hobby.name, hobby.emoji, null, isImpulsive = true)
        assertEquals(true, db.categories().getAllWithSubs().single { it.category.id == hobby.id }.category.isImpulsive)
        // без явного значения флажок сохраняется
        repo.updateCategory(hobby.id, hobby.name, hobby.emoji, null)
        assertEquals(true, db.categories().getAllWithSubs().single { it.category.id == hobby.id }.category.isImpulsive)
    }

    @Test
    fun renameOtherIsForbidden() = runTest {
        val other = cat(DefaultCategories.OTHER).category
        assertCategoryError { repo.updateCategory(other.id, "Разное", other.emoji, null) }
    }

    @Test
    fun archivedHiddenFromActiveButExpensesKept() = runTest {
        val hobby = cat("Хобби").category
        addExpense(hobby.id)
        repo.setArchived(hobby.id, true)
        assertTrue(db.categories().getActiveWithSubs().none { it.category.id == hobby.id })
        assertTrue(db.categories().getAllWithSubs().any { it.category.id == hobby.id })
        assertEquals(1, expensesOf(hobby.id).size)
    }

    @Test
    fun archiveOtherIsForbidden() = runTest {
        val other = cat(DefaultCategories.OTHER).category
        assertCategoryError { repo.setArchived(other.id, true) }
    }

    @Test
    fun moveSwapsWithNeighbourAndKeepsOtherLast() = runTest {
        repo.move(cat("Кафе и рестораны").category.id, up = true)
        val names = db.categories().getActiveWithSubs().map { it.category.name }
        assertEquals(listOf("Кафе и рестораны", "Продукты"), names.take(2))

        val beforeLast = names[names.size - 2]
        repo.move(cat(beforeLast).category.id, up = false)
        val after = db.categories().getActiveWithSubs().map { it.category.name }
        assertEquals(names, after)
        assertEquals(DefaultCategories.OTHER, after.last())
    }

    @Test
    fun mergeMovesExpensesAndSubcategories() = runTest {
        val delivery = cat("Доставка еды").category
        val rolls = repo.addSubcategory(delivery.id, "Роллы")
        addExpense(delivery.id)
        addExpense(delivery.id, rolls)
        val cafe = cat("Кафе и рестораны").category

        assertEquals(2, repo.merge(delivery.id, cafe.id))
        assertEquals(2, expensesOf(cafe.id).size)
        assertEquals(cafe.id, cat("Кафе и рестораны").subs.single { it.name == "Роллы" }.categoryId)
        assertTrue(db.categories().getAllWithSubs().none { it.category.id == delivery.id })
    }

    @Test
    fun mergeUnifiesSameNamedSubcategory() = runTest {
        val delivery = cat("Доставка еды").category
        val srcLunch = repo.addSubcategory(delivery.id, "обед")
        addExpense(delivery.id, srcLunch)
        val cafe = cat("Кафе и рестораны")
        val dstLunch = cafe.subs.single { it.name == "Обед" }.id

        repo.merge(delivery.id, cafe.category.id)
        val subs = cat("Кафе и рестораны").subs
        assertEquals(1, subs.count { it.name.lowercase() == "обед" })
        assertEquals(dstLunch, expensesOf(cafe.category.id).single().expense.subcategoryId)
    }

    @Test
    fun mergeIntoItselfOrFromOtherIsForbidden() = runTest {
        val a = cat("Хобби").category.id
        val other = cat(DefaultCategories.OTHER).category.id
        assertCategoryError { repo.merge(a, a) }
        assertCategoryError { repo.merge(other, a) }
    }

    @Test
    fun unaccountedCannotBeRenamedArchivedOrMerged() = runTest {
        val un = cat(DefaultCategories.UNACCOUNTED).category
        val hobby = cat("Хобби").category
        assertEquals(DefaultCategories.UNACCOUNTED_EMOJI, un.emoji)
        assertCategoryError { repo.updateCategory(un.id, "Разное", un.emoji, null) }
        assertCategoryError { repo.setArchived(un.id, true) }
        assertCategoryError { repo.merge(un.id, hobby.id) }
        assertCategoryError { repo.merge(hobby.id, un.id) }
        assertEquals(DefaultCategories.UNACCOUNTED, cat(DefaultCategories.UNACCOUNTED).category.name)
        assertTrue(!cat(DefaultCategories.UNACCOUNTED).category.isArchived)

        val before = db.categories().getActiveWithSubs().map { it.category.name }
        repo.move(un.id, up = true)
        repo.move(un.id, up = false)
        assertEquals(before, db.categories().getActiveWithSubs().map { it.category.name })
    }

    @Test
    fun ensureUnaccountedIsIdempotent() = runTest {
        val id = cat(DefaultCategories.UNACCOUNTED).category.id
        assertEquals(id, repo.ensureUnaccounted())
        assertEquals(id, repo.ensureUnaccounted())
        assertEquals(26, categoryCount())
        db.categories().delete(cat(DefaultCategories.UNACCOUNTED).category)
        val recreated = repo.ensureUnaccounted()
        assertEquals(DefaultCategories.UNACCOUNTED, cat(DefaultCategories.UNACCOUNTED).category.name)
        assertEquals(recreated, repo.ensureUnaccounted())
        assertEquals(26, categoryCount())
    }

    @Test
    fun deleteSubcategoryNullsExpenseLink() = runTest {
        val products = cat("Продукты")
        val market = products.subs.single { it.name == "Рынок" }.id
        addExpense(products.category.id, market)
        repo.deleteSubcategory(market)
        val e = expensesOf(products.category.id).single().expense
        assertNull(e.subcategoryId)
    }
}
