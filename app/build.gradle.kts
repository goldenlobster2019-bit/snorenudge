plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing is optional: read from ~/.gradle/gradle.properties only if all keys are present.
val signingKeys = listOf(
    "SNORENUDGE_STORE_FILE",
    "SNORENUDGE_STORE_PASSWORD",
    "SNORENUDGE_KEY_ALIAS",
    "SNORENUDGE_KEY_PASSWORD",
)
val hasReleaseSigning = signingKeys.all { providers.gradleProperty(it).isPresent }

android {
    namespace = "ro.goldenlobster.snorenudge"
    compileSdk = 36

    defaultConfig {
        applicationId = "ro.goldenlobster.snorenudge"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Galaxy Watch4 Classic is ARM; Wear OS userspace may be 32-bit, so keep both ARM ABIs.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(providers.gradleProperty("SNORENUDGE_STORE_FILE").get())
                storePassword = providers.gradleProperty("SNORENUDGE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("SNORENUDGE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("SNORENUDGE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.litert)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.concurrent.futures)

    testImplementation(libs.junit)
}
