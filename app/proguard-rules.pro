# Anything the native library reaches by name must survive R8 untouched: JNI
# resolves methods from strings, so a renamed or inlined member fails only at
# runtime, with "NoSuchMethodError: no non-static method ...".
#
# Every entry here has a counterpart in native/src — keep the two in step when
# adding a call across the boundary.

# native fn declarations resolved against this class
-keepclasseswithmembernames class com.abhishek.ame.nativecore.NativeBridge {
    native <methods>;
}

# called from the engine's event pump (native/src/engine.rs)
-keep class com.abhishek.ame.nativecore.NativeEvents { *; }
-keep class * implements com.abhishek.ame.nativecore.NativeEvents { *; }

# start / stop / write, called from the audio sink (native/src/sink.rs)
-keep class com.abhishek.ame.playback.AudioOutput { *; }

# kotlinx.serialization generates serializers reflectively from these.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.abhishek.ame.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit keeps generic signatures on its interface methods.
-keepattributes Signature, RuntimeVisibleAnnotations
-keep,allowobfuscation interface com.abhishek.ame.data.SpotifyApi

# NewPipeExtractor carries Rhino, which is a JavaScript engine written for the
# JVM: it references java.beans, javax.script and jdk.dynalink, none of which
# exists on Android. Every one of them is behind a path this app never takes,
# the scripting-engine front end and the invokedynamic optimiser, so they are
# dead weight rather than a missing dependency and R8 only needs telling not to
# fail on them. Rhino falls back to its interpreter when the optimiser will not
# load, which is what already happens on Android.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.javascript.engine.**
-dontwarn org.mozilla.javascript.optimizer.**
-dontwarn org.mozilla.javascript.tools.**

# Rhino itself must survive intact. It resolves host classes and members by
# name from the JavaScript it runs, and generates bytecode through
# ClassFileWriter, so a renamed member is a failure at run time only: YouTube's
# signature cipher is deciphered by exactly this, and the symptom would be
# every stream failing while search still worked.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }

# Resolved by name from app/src/main/cpp/stretch.cpp, whose JNI symbols embed
# the fully qualified class name.
-keepclasseswithmembernames class com.abhishek.ame.playback.Stretcher { native <methods>; }
-keep class com.abhishek.ame.playback.Stretcher { *; }

# Preserve readable line numbers and stack traces in release builds
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
