# HiveMQ — reflection-heavy networking internals
-keep class com.hivemq.** { *; }
-dontwarn com.hivemq.**

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Timber
-dontwarn org.jetbrains.annotations.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }
