# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
proguard
# 1. Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. Firebase specific rules
# Firebase Firestore and Auth often require keeping specific attributes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.firebase.**

# 3. Cloudinary rules
# Prevents Cloudinary's reflection-based configuration from breaking
-keep class com.cloudinary.** { *; }
-dontwarn com.cloudinary.**

# 4. Coil rules (Image loading)
-keep class coil.** { *; }
-dontwarn coil.**

# 5. General Compose/Kotlin rules
# R8 handles most of this, but keeping Metadata is safer for some reflection libs
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations