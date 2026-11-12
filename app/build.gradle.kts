import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------- Нейроголос Дружка (Piper через sherpa-onnx) ----------------
// Библиотека и голосовая модель большие, поэтому в git их нет: скачиваются при первой сборке.
val sherpaVersion = "1.12.15"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
val piperDir = file("src/main/assets/piper")

fun download(url: String, to: File) {
    if (to.exists()) return
    to.parentFile.mkdirs()
    val part = File(to.path + ".part")
    logger.lifecycle("Скачиваю $url")
    URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
    part.renameTo(to)
}

download("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar", sherpaAar)
if (File(piperDir, ".ok").let { !it.exists() || it.readText() != "irina-int8" }) {
    val archive = layout.buildDirectory.file("piper/irina-int8.tar.bz2").get().asFile
    download("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium-int8.tar.bz2", archive)
    val unpacked = layout.buildDirectory.dir("piper/unpacked").get().asFile
    copy { from(tarTree(resources.bzip2(archive))); into(unpacked) }
    val src = File(unpacked, "vits-piper-ru_RU-irina-medium-int8")
    piperDir.deleteRecursively()
    piperDir.mkdirs()
    File(src, "ru_RU-irina-medium.onnx").copyTo(File(piperDir, "model.onnx"), true)
    File(src, "tokens.txt").copyTo(File(piperDir, "tokens.txt"), true)
    File(src, "MODEL_CARD").copyTo(File(piperDir, "MODEL_CARD"), true)
    // Из словарей eSpeak нужен только русский
    copy {
        from(File(src, "espeak-ng-data"))
        into(File(piperDir, "espeak-ng-data"))
        exclude { it.name.endsWith("_dict") && it.name != "ru_dict" }
    }
    File(piperDir, ".ok").writeText("irina-int8")
}

android {
    namespace = "com.vse.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vse.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3 с Дружком"
        // Нейроголос есть только для ARM — на нём работают почти все телефоны
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
    packaging {
        // Сжимаем нативные библиотеки нейроголоса — APK заметно легче
        jniLibs.useLegacyPackaging = true
    }
    androidResources {
        // Модель голоса читается напрямую из APK — не сжимаем
        noCompress += listOf("onnx")
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
    // Нейросетевой голос без интернета
    implementation(files("libs/sherpa-onnx-$sherpaVersion.aar"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
