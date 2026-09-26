plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.offlinestudy.solver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.offlinestudy.solver"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Native ABIs for the bundled llama.cpp inference library.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig field OFFLINE_ONLY is checked at runtime to hard-fail
        // any accidental network call (see util/NetworkGuard.kt).
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("META-INF/*")
        // Keep the native llama.cpp .so files uncompressed for mmap loading.
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    // --- Jetpack / Compose ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // --- Room (local metadata + chunk + vector storage) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- WorkManager (background indexing) ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- CameraX ---
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // --- Offline OCR: ML Kit BUNDLED text recognition.
    // The "bundled" artifact ships the recognition model inside the APK
    // (no Play Services download, no network at runtime).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-bundled-common:16.0.0")

    // --- Local document parsing ---
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") // Apache-2.0, on-device PDF parsing
    // DOCX is parsed with a small hand-rolled XML reader (see DocxParser) to avoid
    // pulling in full Apache POI, which is heavy for mobile. Uses the platform's
    // built-in XmlPullParser + java.util.zip, no extra dependency required.

    // --- Local embeddings: TensorFlow Lite runtime for the bundled
    // sentence-transformer (.tflite) model in assets/models/embedding.tflite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
