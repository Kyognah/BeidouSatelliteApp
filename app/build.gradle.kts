plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.huawei.beidousatellite"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.huawei.beidousatellite"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = false
    }

    packagingOptions {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    // Core Android
    val core_ktx_version = "1.13.1"
    val appcompat_version = "1.7.0"
    val material_version = "1.12.0"
    val constraintlayout_version = "2.1.4"
    val lifecycle_version = "2.8.2"
    val room_version = "2.6.1"
    val hilt_version = "2.48"
    val coroutines_version = "1.8.0"
    val navigation_version = "2.7.7"
    val work_version = "2.9.0"
    val datastore_version = "1.1.1"
    val protobuf_version = "3.25.3"
    val okhttp_version = "4.12.0"
    val moshi_version = "1.15.1"
    val coil_version = "2.6.0"
    val gson_version = "2.10.1"

    implementation("androidx.core:core-ktx:$core_ktx_version")
    implementation("androidx.appcompat:appcompat:$appcompat_version")
    implementation("com.google.android.material:material:$material_version")
    implementation("androidx.constraintlayout:constraintlayout:$constraintlayout_version")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle_version")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:$navigation_version")
    implementation("androidx.navigation:navigation-ui-ktx:$navigation_version")

    // Room Database
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:$datastore_version")
    implementation("androidx.datastore:datastore-preferences-rxjava3:$datastore_version")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:$hilt_version")
    kapt("com.google.dagger:hilt-compiler:$hilt_version")
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutines_version")

    // WorkManager (background tasks)
    implementation("androidx.work:work-runtime-ktx:$work_version")

    // Networking & Serialization
    implementation("com.squareup.okhttp3:okhttp:$okhttp_version")
    implementation("com.squareup.moshi:moshi-kotlin:$moshi_version")
    implementation("com.squareup.moshi:moshi-adapters:$moshi_version")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:$moshi_version")

    // Gson (for structured logging)
    implementation("com.google.code.gson:gson:$gson_version")

    // Protobuf (for message serialization)
    implementation("com.google.protobuf:protobuf-javalite:$protobuf_version")

    // Image loading
    implementation("io.coil-kt:coil:$coil_version")
    implementation("io.coil-kt:coil-compose:$coil_version")

    // Sensors & Location
    implementation("androidx.lifecycle:lifecycle-process:$lifecycle_version")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:$hilt_version")
    kaptAndroidTest("com.google.dagger:hilt-compiler:$hilt_version")
}