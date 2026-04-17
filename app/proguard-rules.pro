# Keep all your data entities for Firebase and Room
-keep class com.altstudio.kirana.data.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Prevent shrinking of serialized names
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
