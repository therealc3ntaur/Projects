# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.smartsiddur.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.smartsiddur.**$$serializer { *; }

# KosherJava loads nothing reflectively, but keep it intact to be safe
-keep class com.kosherjava.zmanim.** { *; }
