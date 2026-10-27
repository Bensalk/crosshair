plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.crosshair"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.crosshair"
        minSdk = 26; targetSdk = 34
        versionCode = 14; versionName = "1.0"
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("KS_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KS_PASS")
                keyAlias = "crosshair"
                keyPassword = System.getenv("KS_PASS")
                // v2 + v3 signatures: v3 lets the signing key be rotated later without breaking updates.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Test-only libraries (not part of the app): JUnit + Robolectric run Android code on the PC/CI.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
