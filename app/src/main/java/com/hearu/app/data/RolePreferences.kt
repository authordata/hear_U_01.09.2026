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
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class RolePreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AI_MSG_COUNT = intPreferencesKey("ai_msg_count")
        val AI_MSG_DATE = longPreferencesKey("ai_msg_date")
    }

    val activeRoleFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_ROLE]
    }

    val displayNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DISPLAY_NAME] ?: "KindSoul"
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val isBiometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC_ENABLED] ?: false
    }

    val isDarkThemeFlow: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME]
    }

    suspend fun setActiveRole(role: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_ROLE] = role
        }
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
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

    suspend fun getAiMessagesUsedToday(): Int {
        val currentDay = getStartOfDayEpoch()
        var count = 0
        context.dataStore.edit { prefs ->
            val lastDate = prefs[Keys.AI_MSG_DATE] ?: 0L
            count = if (lastDate < currentDay) {
                prefs[Keys.AI_MSG_COUNT] = 0
                prefs[Keys.AI_MSG_DATE] = currentDay
                0
            } else {
                prefs[Keys.AI_MSG_COUNT] ?: 0
            }
        }
        return count
    }

    private fun getStartOfDayEpoch(): Long {
        return LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
