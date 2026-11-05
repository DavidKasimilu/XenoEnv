# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# WebView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve all classes in these packages as they may use reflection or be used by system
-keep class com.example.telecom.** { *; }
-keep class com.example.plugins.** { *; }
-keep class com.example.dpc.** { *; }

# Preserve services and receivers from being stripped or renamed
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.telecom.InCallService

# Data models (POJOs) used for JSON serialization/deserialization
-keep class com.example.data.models.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Coil
-keep class coil.** { *; }

# org.json
-keep class org.json.** { *; }

# Preserve source file names and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
