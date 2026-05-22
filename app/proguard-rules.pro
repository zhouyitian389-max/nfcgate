# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ============== Android components ==============
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

# ============== Data models ==============
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements java.io.Serializable {
    static final long serialVersionUID;
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembernames class * {
    native <methods>;
}

# ============== Library integrations ==============
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

-keep class * extends androidx.lifecycle.ViewModel { *; }

# ============== NFCGate app specific ==============
-keep class de.tu_darmstadt.seemoo.nfcgate.nfc.** { *; }
-keep class de.tu_darmstadt.seemoo.nfcgate.db.model.** { *; }
-keep class de.tu_darmstadt.seemoo.nfcgate.gui.MainActivity { *; }

# ============== Optimizations ==============
-assumenosideeffects public class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-allowaccessmodification
