# Strip debug/verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Preserve annotations and domain/data models used in serialization or diagnostics
-keepattributes *Annotation*
-keep class com.example.data.model.** { *; }
-keep class com.example.data.ai.** { *; }
-keep class com.example.domain.model.** { *; }
