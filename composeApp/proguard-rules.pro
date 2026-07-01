-keep class com.lrcstudio.app.** { *; }
-dontwarn javax.annotation.**

# kotlinx.coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# kotlinx.serialization
-keep,includedescriptorclasses class com.lrcstudio.app.**$$serializer { *; }
-keepclassmembers class com.lrcstudio.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.lrcstudio.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Compose
-dontwarn androidx.compose.**
