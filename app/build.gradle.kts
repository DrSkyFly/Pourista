import java.util.Properties

plugins {
    // From AGP 9 on, Kotlin support is built in and a separate kotlin-android is not needed.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// The signing key and the passwords lie outside the repository. If the file is missing, the
// release is simply built unsigned and the build does not fail.
val keystoreProps = Properties().apply {
    val file = rootProject.file("../keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.pourista"
    compileSdk = 37
    // By default AGP 8.13 asks for build-tools 35.0.0, while 36.0.0 is installed.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.pourista"
        minSdk = 29
        targetSdk = 37
        versionCode = 21
        versionName = "1.9.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // There is one app, but it is given out from two places, and their rules differ. The code is
    // shared: the differences come down to BuildConfig constants.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            // The link to the releases page: an update from there is installed by hand.
            buildConfigField("boolean", "UPDATE_LINK", "true")
            // We look for testers among those who install the APK by hand.
            buildConfigField("boolean", "TESTERS_CALL", "true")
        }
        create("play") {
            dimension = "store"
            // In the store, updating is the store's business, and calling people for an APK past
            // it is not allowed by the Play rules anyway.
            buildConfigField("boolean", "UPDATE_LINK", "false")
            // Whoever installs from Play is a tester already: no need to call them.
            buildConfigField("boolean", "TESTERS_CALL", "false")
        }
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
            // With the same key as the release: otherwise a debug build cannot be installed over
            // one downloaded from GitHub, nor a release build over a debug one, and every check on
            // a live phone would require uninstalling the app.
            // No key (on CI, for instance) — and the ordinary debug signature is left.
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

    bundle {
        // Play gives out of a bundle only the languages of the phone, while our language is chosen
        // in the settings — and before Android 13 by substituting the locale, which needs the
        // resources themselves. So every translation travels in every installation.
        language {
            enableSplit = false
        }
    }
}

room {
    // The schema history lies in the repository: the migrations are checked against it.
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
    // An explicit version: the transitive 1.7.3 is incompatible with room-testing in androidTest.
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Scale
    implementation(libs.blessed.android.coroutines)

    testImplementation(libs.junit)
    // The real org.json instead of the stub from android.jar: recipe parsing is tested.
    testImplementation(libs.json.unit.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
