plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.echo.recorder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.echo.recorder"
        minSdk = 26
        targetSdk = 34
        // CI passes -PversionCode=<run number> so every build is an upgrade.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.0.$versionCode"
    }

    signingConfigs {
        // Stable key so auto-updates install over the previous build.
        create("echo") {
            storeFile = file("../keystore/echo.jks")
            storePassword = "echoapp"
            keyAlias = "echo"
            keyPassword = "echoapp"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("echo")
        }
        debug { signingConfig = signingConfigs.getByName("echo") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
