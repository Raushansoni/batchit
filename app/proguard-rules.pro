# BatchIt / Stream Chat / Firebase / Hilt

-keep class io.getstream.** { *; }
-dontwarn io.getstream.**

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep BatchIt auth/settings models
-keep class io.getstream.whatsappclone.model.** { *; }
-keep class io.getstream.whatsappclone.settings.** { *; }
-keep class io.getstream.whatsappclone.auth.** { *; }
-keep class io.getstream.whatsappclone.status.model.** { *; }

# Credential Manager / Sign in with Google (release minify)
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-dontwarn androidx.credentials.playservices.**
