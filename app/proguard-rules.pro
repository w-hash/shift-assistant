# 班表助手 release 混淆规则

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.lh.shiftassistant.**$$serializer { *; }
-keepclassmembers class com.lh.shiftassistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.lh.shiftassistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room 实体
-keep class com.lh.shiftassistant.data.** { *; }
