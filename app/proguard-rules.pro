# LiteTask ProGuard / R8 Rules

# Retain attributes needed for reflection, annotations, and stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# @Keep annotations
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Native JNI methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Data models, Room entities, Gson serialized backup & API models
-keep class com.litetask.app.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit / OkHttp / Gson
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# Xunfei MSC Speech SDK (JNI / reflection)
-keep class com.iflytek.** { *; }
-dontwarn com.iflytek.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
