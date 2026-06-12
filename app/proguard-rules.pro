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

# --- Advanced Security Code Protection Rules ---

# Bouncy Castle Security Provider Protection
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SQLCipher Encryption Engine Protection
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# Google Play Integrity API SDK Protection
-keep class com.google.android.play.core.integrity.** { *; }
-keep class com.google.android.play.core.tasks.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# Discard sensitive logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

-assumenosideeffects class com.mysql.pocketsql.engine.SqlLog {
    public static void e(...);
    public static void printStackTrace(...);
    public static void err(...);
}

-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Keep AndroidX Security Crypto to avoid false positive MODE_WORLD_READABLE in MobSF due to obfuscation
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep SqlCipherHelper to avoid false positive hardcoded secrets in MobSF due to obfuscation
-keep class com.mysql.pocketsql.engine.SqlCipherHelper { *; }