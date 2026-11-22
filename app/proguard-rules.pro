# Vortex Researcher R8 / ProGuard Configuration
# Preserves reflection-bound Firebase Firestore, Room, Moshi, OkHttp/Retrofit, and Kotlin metadata classes.

-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,SourceFile,LineNumberTable

# --- @Keep & Firestore / Data Models ---
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.example.data.model.** { *; }
-keep class com.example.data.infra.** { *; }
-keep class com.example.data.security.** { *; }
-keep class com.example.data.github.** { *; }
-keep class com.example.data.sync.** { *; }

# --- Room Database & Entities ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.local.cache.** { *; }

# --- Moshi & Kotlin Reflection / Generated Adapters ---
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# --- Retrofit / OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- Firebase & Play Integrity ---
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.lang.model.element.Modifier

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
