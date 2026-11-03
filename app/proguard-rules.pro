# Release R8. Do not keep com.davidlang.vehicleexpensesautomated.**.
# Room, Hilt, kotlinx.serialization, and MSAL apply their own consumer rules.

# Paddle Lite JNI looks up methods and fields by name.
-keep class com.baidu.paddle.lite.** { *; }

# FindClass / GetFieldID / GetMethodID peers. Not a whole-package keep.
# -keep, not -keepnames: R8 shrunk JNI-only members (deskewSetFor, prepareScale, rot-GMM ctors).
-keep class com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine$Companion { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine$DetectionResult { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine$DetectionBox { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.BufferSet { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.BufferSet$** { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.PumpCostVolUtils { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.PumpCostVolUtils$Companion { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.QfRotGmmDetectOut { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.util.QfRotGmmRecOut { *; }
-keep class com.davidlang.vehicleexpensesautomated.ui.ocr.OcrRotGmmOut { *; }

-keepclasseswithmembernames class org.opencv.** {
    native <methods>;
}

# JNI GetFieldID("nativeObj", "J").
-keepclassmembers class org.opencv.core.Mat {
    long nativeObj;
}

# Google Drive/Sheets REST clients (Gson models); release R8 otherwise throws IllegalArgumentException: key error.
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.sheets.** { *; }
-keep class com.google.api.client.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# Apache HttpClient optional JNDI/Kerberos — not on Android.
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# MSAL references these annotations; they are not on the classpath.
-dontwarn edu.umd.cs.findbugs.annotations.NonNull
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
