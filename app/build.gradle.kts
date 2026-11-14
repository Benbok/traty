import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Распознавание речи на телефоне: официальный AAR sherpa-onnx (Kotlin API + нативные библиотеки) с GitHub.
// В git не лежит: сборка скачивает его в build/ и проверяет SHA-256.
val sherpaVersion = "2.0.1"
val sherpaSha256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
val sherpaAar = layout.buildDirectory.file("sherpa/sherpa-onnx-$sherpaVersion.aar")

fun File.sha256(): String =
    MessageDigest.getInstance("SHA-256").also { md ->
        inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
    }.digest().joinToString("") { "%02x".format(it) }

val downloadSherpaAar = tasks.register("downloadSherpaAar") {
    val out = sherpaAar.get().asFile
    outputs.file(out)
    doLast {
        if (out.isFile && out.sha256() == sherpaSha256) return@doLast
        out.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        val tmp = File(out.parentFile, out.name + ".part")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(tmp.sha256() == sherpaSha256) { "Контрольная сумма sherpa-onnx AAR не совпала" }
        tmp.renameTo(out)
    }
}

android {
    namespace = "ru.my.traty"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.my.traty"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // Нативные библиотеки распознавания — только для телефонов arm64 (эмуляторами не пользуемся)
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Для личного использования подписываем release тем же debug-ключом
            signingConfig = signingConfigs.getByName("debug")
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.glance.appwidget)
    implementation(files(sherpaAar).builtBy(downloadSherpaAar))
    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
