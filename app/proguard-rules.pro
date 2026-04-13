# Add project specific ProGuard rules here.
# You can control the set of applied configuration rules using the
# proguardFiles setting in build.gradle.
# By default, the flags in this file are appended to configuration specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
