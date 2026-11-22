# Open Crown release rules.
#
# The protocol core is Rust behind UniFFI; the generated Kotlin must survive R8
# with its JNA/FFI shape intact. The app keeps no privileged Kotlin entry point:
# the agent is a separate binary.

-keep class org.opencrown.ffi.** { *; }
-keep interface org.opencrown.ffi.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Structure { public *; }
-keepclassmembers class * implements com.sun.jna.Library { public *; }
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.sun.jna.**
