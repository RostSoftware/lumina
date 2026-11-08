# Quartz uses kotlinx.serialization and Jackson-free reflection-less parsing; keep serializers.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class rocks.lumina.app.**$$serializer { *; }
-keepclassmembers class rocks.lumina.app.** { *** Companion; }
-keepclasseswithmembers class rocks.lumina.app.** { kotlinx.serialization.KSerializer serializer(...); }
# secp256k1 JNI bindings
-keep class fr.acinq.secp256k1.** { *; }
# Quartz event classes are created reflectively by kind in some paths
-keep class com.vitorpamplona.quartz.** { *; }
-dontwarn com.vitorpamplona.**
-dontwarn org.slf4j.**
