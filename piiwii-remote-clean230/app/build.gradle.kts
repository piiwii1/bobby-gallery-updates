plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "ch.piiwii.remote2clean"
    compileSdk = 35
    defaultConfig {
        applicationId = "ch.piiwii.remote2clean"
        minSdk = 26
        targetSdk = 35
        versionCode = 23001
        versionName = "2.3.0-clean1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
