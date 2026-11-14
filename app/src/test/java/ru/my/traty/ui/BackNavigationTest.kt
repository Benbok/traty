package ru.my.traty.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.ui.budget.BudgetScreen
import ru.my.traty.ui.recurring.RecurringScreen
import ru.my.traty.ui.settings.AiSettingsScreen
import ru.my.traty.ui.settings.RulesScreen

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class BackNavigationTest {

    @Test
    fun budgetScreenHandlesBack() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var backCalled = false

        activity.setContent {
            BudgetScreen(onBack = { backCalled = true })
        }

        assertTrue("Back callback should be enabled when BudgetScreen is open", activity.onBackPressedDispatcher.hasEnabledCallbacks())
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("onBack should be called when system back is pressed in BudgetScreen", backCalled)
    }

    @Test
    fun recurringScreenHandlesBack() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var backCalled = false

        activity.setContent {
            RecurringScreen(onBack = { backCalled = true })
        }

        assertTrue("Back callback should be enabled when RecurringScreen is open", activity.onBackPressedDispatcher.hasEnabledCallbacks())
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("onBack should be called when system back is pressed in RecurringScreen", backCalled)
    }

    @Test
    fun rulesScreenHandlesBack() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var backCalled = false
        val vm = MainViewModel(activity.application as ru.my.traty.TratyApp)

        activity.setContent {
            RulesScreen(vm = vm, onBack = { backCalled = true })
        }

        assertTrue("Back callback should be enabled when RulesScreen is open", activity.onBackPressedDispatcher.hasEnabledCallbacks())
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("onBack should be called when system back is pressed in RulesScreen", backCalled)
    }

    @Test
    fun aiSettingsScreenHandlesBack() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var backCalled = false
        val vm = MainViewModel(activity.application as ru.my.traty.TratyApp)

        activity.setContent {
            AiSettingsScreen(vm = vm, onBack = { backCalled = true })
        }

        assertTrue("Back callback should be enabled when AiSettingsScreen is open", activity.onBackPressedDispatcher.hasEnabledCallbacks())
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("onBack should be called when system back is pressed in AiSettingsScreen", backCalled)
    }

    @Test
    fun rootInitiallyOnHomeHasNoEnabledBackCallback() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val vm = MainViewModel(activity.application as ru.my.traty.TratyApp)

        activity.setContent {
            TratyRoot(vm)
        }

        assertFalse("When on HOME tab with no subpages, no back callback should intercept back", activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }
}
