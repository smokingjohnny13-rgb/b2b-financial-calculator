# Add project specific ProGuard rules here.
# You can check the documentation for R8 / ProGuard for more details.

# Keep source file names and line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations and generic signatures (required for Firebase Firestore & serialization)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Keep application data models and repository classes (for Firestore auto-mapping)
-keep class com.example.** { *; }
-keepclassmembers class com.example.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

# Firebase, Firestore, AI & Google Credential Manager
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
