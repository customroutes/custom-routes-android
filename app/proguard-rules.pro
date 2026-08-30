# Project-specific ProGuard rules.

# ONNX Runtime resolves these JNI-facing classes and members by name.
-keep class ai.onnxruntime.** { *; }

# Keep local performance diagnostics in debug builds only.
-assumenosideeffects class android.util.Log {
    public static int i(java.lang.String, java.lang.String);
}
