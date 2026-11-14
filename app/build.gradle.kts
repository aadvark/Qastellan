plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kithandkeen.qastellan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kithandkeen.qastellan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Where the daily definitions are published by .github/workflows/definitions.yml
        buildConfigField(
            "String",
            "DEFINITIONS_URL",
            "\"https://github.com/aadvark/Qastellan/releases/download/definitions/\"",
        )
        buildConfigField("String", "GITHUB_REPO", "\"aadvark/Qastellan\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // F-Droid: updates come from F-Droid itself; the app only checks and tells the user.
        create("fdroid") {
            dimension = "distribution"
        }
        // GitHub Releases: the app compares its version with the latest release.
        create("github") {
            dimension = "distribution"
        }
    }

    // Release signing: the keystore is decoded from GitHub secrets by .github/workflows/release.yml.
    // Without these environment variables, release builds are left unsigned.
    val keystorePath: String? = System.getenv("QASTELLAN_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("QASTELLAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QASTELLAN_KEY_ALIAS")
                keyPassword = System.getenv("QASTELLAN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Reproducible builds for F-Droid: no build time or VCS info baked into the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json for JVM unit tests
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
