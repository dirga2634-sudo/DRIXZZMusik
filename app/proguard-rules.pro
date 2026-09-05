# Gomouse Pro release (R8/ProGuard) rules.

# ---- Gson --------------------------------------------------------------
# Gson uses reflection to read/write field names, so the model classes
# that get serialized to/from profile JSON must keep their field names.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Gomouse Pro's persisted data model classes verbatim so old profile
# JSON files saved on a user's device keep deserializing correctly across
# app updates.
-keep class com.gomouse.pro.model.** { <fields>; }
-keepclassmembers class com.gomouse.pro.model.** {
    <init>(...);
}

# ---- AccessibilityService / Services -----------------------------------
# Keep our exported service/component classes reachable by name for the
# manifest + system to bind to.
-keep class com.gomouse.pro.service.** { *; }

# ---- General Android ------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
