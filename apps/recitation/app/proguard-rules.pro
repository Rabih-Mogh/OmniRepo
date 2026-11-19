# ONNX Runtime calls into its Java classes from native code.
-keep class ai.onnxruntime.** { *; }

# NewPipe Extractor runs YouTube's player script with Rhino, which finds its classes by name.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
# The extractor finds some of its own classes by name too (time-ago patterns, protobuf messages).
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.mozilla.javascript.tools.**
# Rhino's compiler needs parts of desktop Java; on Android it interprets scripts instead.
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
