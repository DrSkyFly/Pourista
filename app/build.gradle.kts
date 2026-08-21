import java.util.Properties

plugins {
    // Начиная с AGP 9 поддержка Kotlin встроена, отдельный kotlin-android не нужен.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Ключ подписи и пароли лежат вне репозитория. Если файла нет — релиз просто
// собирается неподписанным, сборка не падает.
val keystoreProps = Properties().apply {
    val file = rootProject.file("../keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.pourista"
    compileSdk = 37
    // По умолчанию AGP 8.13 просит build-tools 35.0.0, а установлены 36.0.0.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.pourista"
        minSdk = 29
        targetSdk = 37
        versionCode = 5
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            // Тем же ключом, что и релиз: иначе отладочную сборку не поставить
            // поверх скачанной с GitHub, а релизную — поверх отладочной, и
            // каждая проверка на живом телефоне требует сноса приложения.
            // Ключа нет (например, на CI) — остаётся обычная отладочная подпись.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

room {
    // История схем лежит в репозитории: по ней проверяются миграции.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // Явная версия: транзитивная 1.7.3 несовместима с room-testing в androidTest.
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Данные
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Весы
    implementation(libs.blessed.android.coroutines)

    testImplementation(libs.junit)
    // Настоящий org.json вместо заглушки из android.jar: разбор рецептов тестируется.
    testImplementation(libs.json.unit.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
