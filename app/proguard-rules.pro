# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ViewModel Protection
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Protecting data models
-keep class com.radioacoustick.opennec2.viewer.domain.** { *; }
-keepclassmembers class com.radioacoustick.opennec2.viewer.domain.** { *; }
-keep class com.radioacoustick.opennec2.viewer.nec.** { *; }
-keepclassmembers class com.radioacoustick.opennec2.viewer.nec.** { *; }

# Protection Gson annotations
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===================================================================
# Google Filament Guidelines (JNI Security, Native Handles, and Reflection)
# ===================================================================

# Protecting the FilamentViewer class from obfuscation and field/method removal
-keep class com.radioacoustick.opennec2.viewer.ui.geometry.FilamentViewer { *; }
-keepclassmembers class com.radioacoustick.opennec2.viewer.ui.geometry.FilamentViewer { *; }

# 1. Protection of all classes and internal Filament packages
-keep class com.google.android.filament.** { *; }
-keepclassmembers class com.google.android.filament.** { *; }

# 2. Protecting Filament's auxiliary utilities (gltfio, utils, cameraprovider)
-keep class com.google.android.filament.gltfio.** { *; }
-keepclassmembers class com.google.android.filament.gltfio.** { *; }

-keep class com.google.android.filament.utils.** { *; }
-keepclassmembers class com.google.android.filament.utils.** { *; }

# 3. Protecting native pointers (long mNativeObject)
#    Filament stores pointers to C++ objects in long fields. R8 must not remove or modify them!
-keepclassmembers class * {
    long mNativeObject;
}

# 4. Protecting Filament JNI Methods
-keepclasseswithmembernames,includedescriptorclasses class com.google.android.filament.** {
    native <methods>;
}