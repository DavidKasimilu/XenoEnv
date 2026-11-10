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

# Preserve WebView JS Bridge implementations across screens and components
-keep class com.example.ui.screens.WhatsAppCallJsBridge { *; }
-keep class com.example.ui.screens.YouTubeOfflineJsBridge { *; }
-keep class com.example.ui.screens.** { *; }
-keep class com.example.ui.components.XenoSwipeBridge { *; }
-keep class com.example.ui.components.GlobalSwipeDownTracker** { *; }

# Preserve all classes in telecom, plugins, and dpc packages
-keep class com.example.telecom.** { *; }
-keep class com.example.plugins.** { *; }
-keep class com.example.dpc.** { *; }

# Preserve services, receivers, and InCallService
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.telecom.InCallService

# Data models & POJOs used for serialization, reflection, and caching
-keep class com.example.data.models.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.util.** { *; }

# Keep ViewModels and their public methods
-keep class * extends androidx.lifecycle.ViewModel { *; }

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Coil image loading
-keep class coil.** { *; }

# org.json
-keep class org.json.** { *; }

# Preserve source file names and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
