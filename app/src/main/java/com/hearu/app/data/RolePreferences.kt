package com.hearu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class RolePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.dataStore)

    constructor(
        context: Context,
        clock: Clock,
        zoneId: ZoneId
    ) : this(context.dataStore, clock, zoneId)

    private object Keys {
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AI_MSG_COUNT = intPreferencesKey("ai_msg_count")
        val AI_MSG_DATE = longPreferencesKey("ai_msg_date")
    }

    val activeRoleFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_ROLE]
    }

    val displayNameFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.DISPLAY_NAME] ?: "KindSoul"
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val onboardingCompletedFlow: Flow<Boolean>
        get() = isOnboardingCompletedFlow

    val isBiometricEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC_ENABLED] ?: false
    }

    val isDarkThemeFlow: Flow<Boolean?> = dataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME]
    }

    suspend fun setActiveRole(role: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_ROLE] = role
        }
    }

    suspend fun setDisplayName(name: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    /**
     * Atomically checks quota and increments in a single DataStore transaction.
     * Returns true if quota was available and was consumed; false if exhausted.
     */
    suspend fun tryConsumeAiQuota(limit: Int = 50): Boolean {
        val currentDay = getStartOfDayEpoch()
        var consumed = false
        dataStore.edit { prefs ->
            val lastDate = prefs[Keys.AI_MSG_DATE] ?: 0L
            val currentCount = if (lastDate < currentDay) 0 else (prefs[Keys.AI_MSG_COUNT] ?: 0)
            if (currentCount < limit) {
                prefs[Keys.AI_MSG_COUNT] = currentCount + 1
                prefs[Keys.AI_MSG_DATE] = currentDay
                consumed = true
            }
        }
        return consumed
    }

    /**
     * Strictly read-only check of AI messages used today.
     * Returns 0 if the stored date is from a previous day without mutating DataStore.
     */
    suspend fun getAiMessagesUsedToday(): Int {
        val currentDay = getStartOfDayEpoch()
        val prefs = dataStore.data.first()
        val lastDate = prefs[Keys.AI_MSG_DATE] ?: 0L
        return if (lastDate < currentDay) 0 else (prefs[Keys.AI_MSG_COUNT] ?: 0)
    }

    suspend fun clearSessionData() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_ROLE)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.BIOMETRIC_ENABLED)
            prefs.remove(Keys.AI_MSG_COUNT)
            prefs.remove(Keys.AI_MSG_DATE)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun getStartOfDayEpoch(): Long {
        return clock.instant().atZone(zoneId).toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
