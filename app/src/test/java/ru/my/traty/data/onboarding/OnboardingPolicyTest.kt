package ru.my.traty.data.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPolicyTest {
    @Test
    fun showsOnlyToNewPersonWithEmptyBase() {
        assertTrue(OnboardingPolicy.showOnLaunch(seen = false, hasRecords = false))
        assertFalse(OnboardingPolicy.showOnLaunch(seen = true, hasRecords = false))
        assertFalse(OnboardingPolicy.showOnLaunch(seen = false, hasRecords = true))
        assertFalse(OnboardingPolicy.showOnLaunch(seen = true, hasRecords = true))
    }

    @Test
    fun personUpdatingWithRecordsIsMarkedSilently() {
        assertTrue(OnboardingPolicy.markSeenSilently(seen = false, hasRecords = true))
        assertFalse(OnboardingPolicy.markSeenSilently(seen = true, hasRecords = true))
        assertFalse(OnboardingPolicy.markSeenSilently(seen = false, hasRecords = false))
    }

    @Test
    fun periodStartFromFirstDayIgnoresSalaryDay() {
        assertEquals(1, OnboardingPolicy.periodStartDay(fromSalary = false, salaryDay = 10))
    }

    @Test
    fun periodStartFromSalaryIsClampedToAllowedDays() {
        assertEquals(10, OnboardingPolicy.periodStartDay(fromSalary = true, salaryDay = 10))
        assertEquals(28, OnboardingPolicy.periodStartDay(fromSalary = true, salaryDay = 31))
        assertEquals(1, OnboardingPolicy.periodStartDay(fromSalary = true, salaryDay = 0))
    }
}
