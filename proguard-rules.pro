# instant app
-keep class com.google.android.instantapps.InstantApps {
  public boolean isInstantApp(...);
}

# glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

# dagger
-dontwarn com.google.errorprone.annotations.*

# retrofit & okhttp
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers interface * {
    @retrofit2.* <methods>;
}
-dontwarn okhttp3.**
-dontnote retrofit2.Platform
-dontwarn retrofit2.Platform$Java8
# Also you must note that if you are using GSON for conversion from JSON to POJO representation, you must ignore those POJO classes from being obfuscated.
# Here include the POJO's that have you have created for mapping JSON response to POJO for example.
-keep class io.jitrapon.glom.**.**Request {*; }
-keep class io.jitrapon.glom.**.**Response {*; }
