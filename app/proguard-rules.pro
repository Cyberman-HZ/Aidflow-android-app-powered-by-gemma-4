# LiteRT-LM internals — keep all
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Compose
-keep class androidx.compose.** { *; }
