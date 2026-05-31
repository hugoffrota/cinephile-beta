# ProGuard rules for Cinephile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keep class com.thalos.cinephile.data.remote.** { *; }
-keep class com.thalos.cinephile.data.local.** { *; }
