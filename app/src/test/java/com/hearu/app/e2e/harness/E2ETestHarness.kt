package com.hearu.app.e2e.harness

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.dataStore
import com.hearu.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import java.io.File

object E2ETestHarness {

    fun resolveProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").canonicalFile
        return generateSequence(start) { it.parentFile }
            .take(6)
            .firstOrNull { dir ->
                (File(dir, "settings.gradle.kts").isFile || File(dir, "settings.gradle").isFile) &&
                    File(dir, "app/src/main/AndroidManifest.xml").isFile
            }
            ?: error("Unable to locate HearU project root starting from: $start")
    }

    fun createInMemoryDb(
        context: Context = ApplicationProvider.getApplicationContext()
    ): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    suspend fun clearRolePreferences(
        context: Context = ApplicationProvider.getApplicationContext()
    ) {
        context.dataStore.edit { it.clear() }
    }

    fun createRolePreferences(
        context: Context = ApplicationProvider.getApplicationContext()
    ): RolePreferences {
        return RolePreferences(context)
    }

    fun createIsolatedDataStore(
        scope: CoroutineScope,
        tempDir: File,
        fileName: String = "e2e_prefs_${System.nanoTime()}.preferences_pb"
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir, fileName) }
        )
    }

    fun createTestNavController(
        context: Context = ApplicationProvider.getApplicationContext()
    ): TestNavHostController {
        return TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
    }

    @Synchronized
    fun ensureFirebaseInitialized(
        context: Context = ApplicationProvider.getApplicationContext()
    ) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:100000000000:android:abcdef0123456789")
                .setApiKey("AIzaSyFakeKeyForInitialScaffoldingTemplate")
                .setProjectId("hearu-production")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }
}
