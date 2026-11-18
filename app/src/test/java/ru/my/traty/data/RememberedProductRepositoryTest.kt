package ru.my.traty.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RememberedProductRepositoryTest {
    @Test
    fun savesEditsAndRemovesNamesAcrossRepositoryInstances() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = RememberedProductRepository(context)
        val saved = RememberedProduct("Чудо молоко", listOf("Чудо кокт. шоколад 3%", "Чудо коктейль 200 мл"))
        assertTrue(repository.save(saved))

        val reopened = RememberedProductRepository(context)
        assertEquals(listOf(saved), reopened.current())
        assertFalse(reopened.save(saved.copy(phrase = "чудо молока")))
        val renamed = saved.copy(phrase = "Шоколадное чудо")
        assertTrue(reopened.save(renamed, previousPhrase = saved.phrase))
        assertEquals(listOf(renamed), repository.current())
        reopened.remove(renamed.phrase)
        assertTrue(repository.current().isEmpty())
    }
}
