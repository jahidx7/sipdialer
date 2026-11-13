# LinPhone SDK
-keep class org.linphone.** { *; }
-keep class org.oze.** { *; }
-dontwarn org.linphone.**

# Release-এ verbose/debug/info লগ সরিয়ে ফেলা
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
