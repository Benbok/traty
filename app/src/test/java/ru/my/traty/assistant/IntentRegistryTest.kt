package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.CategoryWithSubs
import java.time.LocalDate

class IntentRegistryTest {
    @Test fun everyIntentConstantIsRegisteredOnce() {
        val consts = Intents::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .mapNotNull { runCatching { it.isAccessible = true; it.get(null) as String }.getOrNull() }
        assertEquals(consts.toSet(), Intents.ALL.toSet())
        assertEquals(Intents.ALL.size, Intents.ALL.toSet().size)
    }

    @Test fun promptListsEveryIntentWithItsHint() {
        val prompt = AssistantPrompts.querySystem(emptyList<CategoryWithSubs>(), LocalDate.of(2026, 11, 14), 1)
        IntentRegistry.ALL.forEach { assertTrue(it.key, "- ${it.key} — ${it.hint}" in prompt) }
        assertTrue("- unknown — вопрос не про траты и доходы." in prompt)
    }

    @Test fun commandsAreTheOnesThatChangeData() {
        assertTrue(Intents.SET_PLAN in IntentRegistry.COMMANDS && Intents.DELETE_EXPENSE in IntentRegistry.COMMANDS)
        assertFalse(Intents.TOTAL in IntentRegistry.COMMANDS || Intents.DEBTS in IntentRegistry.COMMANDS)
    }

    @Test fun debtGroup() {
        assertEquals(
            setOf(Intents.DEBTS, Intents.DELETE_DEBT, Intents.CLOSE_DEBT, Intents.CANCEL_DEBT),
            IntentRegistry.inGroup(IntentRegistry.DEBT),
        )
    }
}
