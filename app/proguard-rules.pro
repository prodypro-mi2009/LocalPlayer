# Media3, Room and Compose ship their own consumer rules.

# jaudiotagger (tag reading/writing): reflection-heavy; its desktop image classes are never used on Android.
-keep class org.jaudiotagger.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn sun.**
-dontwarn org.jaudiotagger.**
-dontwarn okio.**

# yt-dlp for Android: Python/FFmpeg launchers and the JSON mapper are reached by reflection.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.**
-dontwarn org.brotli.**
-dontwarn com.aayushatharva.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class org.apache.commons.** { *; }
-keep class kotlin.Metadata { *; }
