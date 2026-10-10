# Media3, Room and Compose ship their own consumer rules.

# jaudiotagger (tag reading/writing): reflection-heavy; its desktop image classes are never used on Android.
-keep class org.jaudiotagger.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn sun.**
-dontwarn org.jaudiotagger.**
-dontwarn okio.**
