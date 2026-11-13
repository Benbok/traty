package ru.my.traty.ui

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class MainViewModelDateTest {
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TratyApp>()
        vm = MainViewModel(app)
    }

    @Test
    fun testDateSelectionAndNavigation() = runTest {
        assertNull(vm.selectedDate.value)

        val targetDate = LocalDate.of(2026, 8, 14)
        vm.selectDate(targetDate)

        assertEquals(targetDate, vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 8), vm.month.value)

        // Day navigation
        vm.prevPeriod()
        assertEquals(LocalDate.of(2026, 8, 13), vm.selectedDate.value)

        vm.nextPeriod()
        assertEquals(targetDate, vm.selectedDate.value)

        // Month navigation clears date filter
        vm.nextMonth()
        assertNull(vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 9), vm.month.value)

        vm.selectDate(LocalDate.of(2026, 9, 20))
        vm.prevMonth()
        assertNull(vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 8), vm.month.value)

        // Clear date filter manually
        vm.selectDate(LocalDate.of(2026, 8, 5))
        vm.clearSelectedDate()
        assertNull(vm.selectedDate.value)
        assertEquals(YearMonth.of(2026, 8), vm.month.value)
    }
}
