# ProGuard rules for BeidouSatelliteApp

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# Keep Room entities and DAOs
-keep class com.huawei.beidousatellite.data.repository.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep data models
-keep class com.huawei.beidousatellite.data.model.** { *; }

# Keep serialization
-keep class kotlinx.serialization.** { *; }
-keep class com.squareup.moshi.** { *; }

# Keep Protobuf
-keep class com.google.protobuf.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Hilt ViewModel
-keep class androidx.hilt.** { *; }
-keep class dagger.hilt.android.lifecycle.** { *; }

# Keep Navigation
-keep class androidx.navigation.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep RxJava
-keep class io.reactivex.** { *; }

# Keep Coil
-keep class coil.** { *; }

# Prevent obfuscation of resource references
-keep class com.huawei.beidousatellite.R { *; }
-keep class com.huawei.beidousatellite.R$* { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable { *; }

# Keep Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep callback methods
-keepclassmembers class * {
    public void *(android.view.View);
}

# Keep setters/getters
-keepclassmembers class * {
    public <fields>;
    public <methods>;
}

# Keep for reflection
-keep class com.huawei.beidousatellite.** { *; }

# HMS Core classes (may be accessed via reflection)
-keep class com.huawei.hms.** { *; }
-keep class com.huawei.rsmc.** { *; }
-keep class com.huawei.lbs.** { *; }