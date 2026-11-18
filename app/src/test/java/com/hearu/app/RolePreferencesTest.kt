package com.hearu.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.hearu.app.data.RolePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RolePreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class MutableClock(
        private var currentInstant: Instant,
        private val zoneId: ZoneId = ZoneId.of("UTC")
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant
        fun setInstant(instant: Instant) {
            currentInstant = instant
        }
    }

    private fun createPreferences(
        clock: Clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneId.of("UTC")),
        zoneId: ZoneId = ZoneId.of("UTC"),
        fileName: String = "prefs_${System.nanoTime()}.preferences_pb"
    ): Pair<RolePreferences, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        val dataStore = PreferenceDataStoreFactory.create {
            tempFolder.newFile(fileName)
        }
        return RolePreferences(dataStore, clock, zoneId) to dataStore
    }

    @Test
    fun `default flow values match expected defaults`() = runTest {
        val (prefs, _) = createPreferences()

        assertNull(prefs.activeRoleFlow.first())
        assertEquals("KindSoul", prefs.displayNameFlow.first())
        assertFalse(prefs.isOnboardingCompletedFlow.first())
        assertFalse(prefs.onboardingCompletedFlow.first())
        assertFalse(prefs.isBiometricEnabledFlow.first())
        assertNull(prefs.isDarkThemeFlow.first())
        assertSame(prefs.isOnboardingCompletedFlow, prefs.onboardingCompletedFlow)
    }

    @Test
    fun `setters update all preference flows`() = runTest {
        val (prefs, _) = createPreferences()

        prefs.setActiveRole("giver")
        prefs.setDisplayName("GentleOak")
        prefs.setOnboardingCompleted(true)
        prefs.setBiometricEnabled(true)
        prefs.setDarkTheme(true)

        assertEquals("giver", prefs.activeRoleFlow.first())
        assertEquals("GentleOak", prefs.displayNameFlow.first())
        assertTrue(prefs.isOnboardingCompletedFlow.first())
        assertTrue(prefs.onboardingCompletedFlow.first())
        assertTrue(prefs.isBiometricEnabledFlow.first())
        assertEquals(true, prefs.isDarkThemeFlow.first())
    }

    @Test
    fun `getAiMessagesUsedToday is strictly read-only when stored date is from previous day`() = runTest {
        val zone = ZoneId.of("UTC")
        val todayInstant = Instant.parse("2026-11-17T10:00:00Z")
        val yesterdayEpoch = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
        val (prefs, dataStore) = createPreferences(
            clock = Clock.fixed(todayInstant, zone),
            zoneId = zone
        )

        val countKey = intPreferencesKey("ai_msg_count")
        val dateKey = longPreferencesKey("ai_msg_date")
        dataStore.edit {
            it[countKey] = 50
            it[dateKey] = yesterdayEpoch
        }

        val usedToday = prefs.getAiMessagesUsedToday()
        assertEquals(0, usedToday)

        // Verify DataStore was NOT mutated on read
        val rawPrefs = dataStore.data.first()
        assertEquals(50, rawPrefs[countKey])
        assertEquals(yesterdayEpoch, rawPrefs[dateKey])
    }

    @Test
    fun `tryConsumeAiQuota enforces daily limit and resets on midnight rollover`() = runTest {
        val zone = ZoneId.of("America/New_York")
        val clock = MutableClock(Instant.parse("2026-11-17T23:55:00-05:00"), zone)
        val (prefs, _) = createPreferences(clock = clock, zoneId = zone)

        repeat(50) { index ->
            assertTrue("Expected consumption #${index + 1} to succeed", prefs.tryConsumeAiQuota(50))
        }
        assertFalse("51st message on same day must be rejected", prefs.tryConsumeAiQuota(50))
        assertEquals(50, prefs.getAiMessagesUsedToday())

        // Advance past midnight local time
        clock.setInstant(Instant.parse("2026-11-18T00:05:00-05:00"))
        assertEquals(0, prefs.getAiMessagesUsedToday())
        assertTrue("Quota should reset after midnight rollover", prefs.tryConsumeAiQuota(50))
        assertEquals(1, prefs.getAiMessagesUsedToday())
    }

    @Test
    fun `clearSessionData clears session keys while preserving onboarding and dark theme`() = runTest {
        val (prefs, _) = createPreferences()

        prefs.setActiveRole("seeker")
        prefs.setDisplayName("CalmRiver")
        prefs.setOnboardingCompleted(true)
        prefs.setBiometricEnabled(true)
        prefs.setDarkTheme(false)
        assertTrue(prefs.tryConsumeAiQuota(50))

        prefs.clearSessionData()

        assertNull(prefs.activeRoleFlow.first())
        assertEquals("KindSoul", prefs.displayNameFlow.first())
        assertFalse(prefs.isBiometricEnabledFlow.first())
        assertEquals(0, prefs.getAiMessagesUsedToday())
        // Preserved app-level preferences
        assertTrue(prefs.isOnboardingCompletedFlow.first())
        assertEquals(false, prefs.isDarkThemeFlow.first())
    }

    @Test
    fun `clearAll removes every stored preference key`() = runTest {
        val (prefs, _) = createPreferences()

        prefs.setActiveRole("both")
        prefs.setDisplayName("Starlight")
        prefs.setOnboardingCompleted(true)
        prefs.setBiometricEnabled(true)
        prefs.setDarkTheme(true)
        prefs.tryConsumeAiQuota(50)

        prefs.clearAll()

        assertNull(prefs.activeRoleFlow.first())
        assertEquals("KindSoul", prefs.displayNameFlow.first())
        assertFalse(prefs.isOnboardingCompletedFlow.first())
        assertFalse(prefs.isBiometricEnabledFlow.first())
        assertNull(prefs.isDarkThemeFlow.first())
        assertEquals(0, prefs.getAiMessagesUsedToday())
    }
}
