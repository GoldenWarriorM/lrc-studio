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

# JNA (Java Native Access) - must keep all classes and methods
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# Apache FOP: only the Liang hyphenation engine is used, the rendering stack
# it ships with references optional classes that the app never loads.
-keep class org.apache.fop.hyphenation.** { *; }
-dontwarn org.apache.fop.**
-dontwarn org.apache.xmlgraphics.**
-dontwarn java.awt.**

# Compose
-dontwarn androidx.compose.**
