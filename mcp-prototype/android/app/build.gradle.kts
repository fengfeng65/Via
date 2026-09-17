plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fengfeng65.viamcp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fengfeng65.viamcp"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1-diagnostic"
    }
}

kotlin { jvmToolchain(17) }
