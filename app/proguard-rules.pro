-keep class com.vse.launcher.LockAccessibilityService { *; }

# Claude API SDK (помощник): модели разбираются через Jackson по рефлексии
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.errorprone.annotations.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn com.github.victools.**
