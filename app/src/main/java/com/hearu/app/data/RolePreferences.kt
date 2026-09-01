package com.hearu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class RolePreferences @Inject constructor(@ApplicationContext private val context: Context) {
    
    private object Keys {
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val AI_MSG_COUNT = intPreferencesKey("ai_msg_count")
        val AI_MSG_DATE = longPreferencesKey("ai_msg_date")
    }

    val activeRoleFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_ROLE]
    }

    suspend fun setActiveRole(role: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_ROLE] = role
        }
    }

    suspend fun getAiMessagesUsedToday(): Int {
        val prefs = context.dataStore.data.first()
        val lastDate = prefs[Keys.AI_MSG_DATE] ?: 0L
        val currentDay = getStartOfDayEpoch()

        return if (lastDate < currentDay) {
            context.dataStore.edit {
                it[Keys.AI_MSG_COUNT] = 0
                it[Keys.AI_MSG_DATE] = currentDay
            }
            0
        } else {
            prefs[Keys.AI_MSG_COUNT] ?: 0
        }
    }

    suspend fun incrementAiMessageCount(): Int {
        val currentDay = getStartOfDayEpoch()
        var newCount = 1
        context.dataStore.edit { prefs ->
            val lastDate = prefs[Keys.AI_MSG_DATE] ?: 0L
            if (lastDate < currentDay) {
                prefs[Keys.AI_MSG_COUNT] = 1
                prefs[Keys.AI_MSG_DATE] = currentDay
                newCount = 1
            } else {
                val current = prefs[Keys.AI_MSG_COUNT] ?: 0
                newCount = current + 1
                prefs[Keys.AI_MSG_COUNT] = newCount
            }
        }
        return newCount
    }

    private fun getStartOfDayEpoch(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
