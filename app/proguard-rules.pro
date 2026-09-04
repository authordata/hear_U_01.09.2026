# ProGuard & R8 Optimization Rules for HearU

# General attributes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Keep Domain Models & Data Classes (Firestore deserialization)
-keep class com.hearu.app.model.** { *; }
-keepclassmembers class com.hearu.app.model.** { *; }

# Keep Firebase SDKs & Cloud Messaging
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.hearu.app.service.HearUFirebaseMessagingService { *; }

# Keep Audio Engine & Voice Note Controller
-keep class com.hearu.app.audio.** { *; }
-keepclassmembers class com.hearu.app.audio.** { *; }

# Keep Navigation Routes & Onboarding
-keep class com.hearu.app.navigation.** { *; }
-keep class com.hearu.app.ui.onboarding.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Room DAOs, Database & Entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.hearu.app.data.local.entity.** { *; }
-keep class com.hearu.app.data.local.dao.** { *; }

# Keep Dagger Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep class dagger.hilt.** { *; }

# Keep Compose Runtime & UI
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.material3.** { *; }
