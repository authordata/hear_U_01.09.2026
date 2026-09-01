# Keep Firebase Models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.hearu.app.model.** { *; }
-keep class com.google.firebase.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Room DAOs and Entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Dagger Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep class dagger.hilt.** { *; }
