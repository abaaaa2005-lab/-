plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vse.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vse.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2 с Дружком"
    }

    // Постоянный ключ (если лежит рядом, в git не хранится): новые версии ставятся поверх старых
    val keystore = file("vse-launcher.keystore")
    signingConfigs {
        create("vse") {
            storeFile = keystore
            storePassword = "vselauncher"
            keyAlias = "vse"
            keyPassword = "vselauncher"
        }
    }
    val sign = if (keystore.exists()) signingConfigs.getByName("vse") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = sign
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = sign
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Claude API — помощник «Дружок» смотрит на экран и комментирует
    implementation("com.anthropic:anthropic-java:3.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
