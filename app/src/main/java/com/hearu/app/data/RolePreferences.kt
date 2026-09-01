package com.hearu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class RolePreferences @Inject constructor(@ApplicationContext private val context: Context) {
    
    private object Keys {
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
    }

    val activeRoleFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_ROLE]
    }

    suspend fun setActiveRole(role: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_ROLE] = role
        }
    }
}
