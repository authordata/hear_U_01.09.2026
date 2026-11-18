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

# Keep Room DAOs, Database, Entities & TypeConverters
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-keep class com.hearu.app.data.local.** { *; }

# Keep Dagger Hilt
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * {
    <init>(...);
}
-keep class dagger.hilt.** { *; }

# Keep BiometricHelper
-keep class com.hearu.app.auth.BiometricHelper { *; }

# Keep Compose Runtime & UI
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.material3.** { *; }

# Firebase Vertex AI, Ktor & Kotlinx Serialization
-keep class com.google.firebase.vertexai.** { *; }
-keep,includedescriptorclasses class com.google.firebase.vertexai.**$$serializer { *; }
-keepclassmembers class com.google.firebase.vertexai.** {
    *** Companion;
}
-keepclasseswithmembers class com.google.firebase.vertexai.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.SerializationKt
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.atomicfu.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Strip debug/verbose Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
