package ru.my.traty.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.R
import ru.my.traty.data.DefaultCategories

class TratyCategoryIconsTest {
    @Test
    fun everyDefaultCategoryHasItsOwnIllustration() {
        val illustrations = DefaultCategories.list.map { (category, _) ->
            categoryIllustration(category.emoji).also { assertNotNull(category.name, it) }
        }

        assertEquals(illustrations.size, illustrations.distinct().size)
    }

    @Test
    fun acceptsEmojiWithAndWithoutPresentationSelector() {
        assertEquals(R.drawable.ic_category_travel, categoryIllustration("✈"))
        assertEquals(R.drawable.ic_category_travel, categoryIllustration("✈️"))
    }

    @Test
    fun leavesUnmappedEmojiToTheBadgeFallback() {
        assertNull(categoryIllustration("🎵"))
        assertNull(categoryIllustration(""))
    }
}
