# GigRun ProGuard rules (R8 currently disabled; this file keeps the release
# block honest so enabling minify can't fail on a missing file).
# Keep Hilt / Room / serialization entry points when minify is enabled.
-keep class dagger.hilt.** { *; }
-keep class androidx.room.** { *; }
-keep class com.gigrun.data.database.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class kotlinx.serialization.** { *; }
