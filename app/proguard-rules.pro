# Aturan Proguard/R8 untuk Music Player.
# minifyEnabled saat ini false pada kedua build type, jadi file ini
# tidak aktif dipakai, namun tetap disediakan agar konfigurasi
# proguardFiles pada build.gradle valid dan siap dipakai bila
# suatu saat minifyEnabled diaktifkan untuk build release.

# Jaga nama class Activity/Service karena direferensikan dari AndroidManifest.xml
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Jaga model data agar field-nya tidak dihapus/di-obfuscate
-keep class com.musicplayer.app.model.** { *; }

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
