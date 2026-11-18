# Open Crown release rules.
#
# The agent is a separate native binary; the app's Kotlin surface is small.
# Keep the JNI entry points once the UniFFI-generated bindings land.

# -keep class org.opencrown.ffi.** { *; }
