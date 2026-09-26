# Keep JNI-facing methods in LocalLlmEngine so R8 doesn't strip/rename them
-keepclasseswithmembers class com.offlinestudy.solver.llm.LocalLlmEngine {
    native <methods>;
}
-keep class com.offlinestudy.solver.database.** { *; }
