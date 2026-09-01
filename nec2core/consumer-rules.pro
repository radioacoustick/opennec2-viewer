# 1. Protect all native (C/C++) methods in all classes of the nec2core module
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 2. Protecting public wrapper classes and JNI interfaces (so that C++ can call back Java methods)
-keep class com.radioacoustick.nec2core.** { *; }
-keepclassmembers class com.radioacoustick.nec2core.** { *; }