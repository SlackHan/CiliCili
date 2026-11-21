# Keep kotlinx.serialization serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lanhan.cilicili.domain.model.**$$serializer { *; }
-keepclassmembers class com.lanhan.cilicili.domain.model.** {
    *** Companion;
}

# Jsoup & JsoupXpath
-keep class org.seimicrawler.xpath.** { *; }
-dontwarn org.seimicrawler.xpath.**
-dontwarn org.antlr.v4.runtime.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
