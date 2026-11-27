# kotlinx-serialization and OkHttp ship their own R8 rules; the manifest's service is kept by the build.

# Keep line numbers in a release stack trace, under one file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The wallpaper API's parcelables cross the binder to the launcher: their CREATORs are read by name.
-keep class ca.dstudio.atvlauncher.wallpaper.api.** implements android.os.Parcelable {
    public static final ** CREATOR;
}
