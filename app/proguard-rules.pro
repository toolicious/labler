# LaBLEr Release: shrinking + resource shrinking WITHOUT obfuscation.
# Goal: smaller APK, but still readable stack traces and no risks from
# renamed serial names (polymorphic label elements / export-import).
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature,*Annotation*

# ---- kotlinx.serialization -------------------------------------------------
# Do not discard generated serializers and the serializer() accesses.
-keepclassmembers class **$$serializer { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Additionally secure our serializable model types explicitly (templates, elements).
-keep @kotlinx.serialization.Serializable class io.github.toolicious.labler.** { *; }
-keepclassmembers class io.github.toolicious.labler.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- ZXing (barcode/QR generation) ------------------------------------------
# The encode path references the writers directly; keep as a safeguard against shrinking.
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# Room, Compose and DataStore ship their own consumer rules, so nothing here.
