import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing from the shared folder outside the repo (../_signing, never in Git).
// If the properties or keystore file is missing (debug build, CI without secret, another machine),
// the release build simply stays unsigned instead of failing.
val signingProps = rootProject.file("../_signing/labler.properties")
val signing = if (signingProps.exists()) {
    Properties().apply { signingProps.inputStream().use { load(it) } }
} else {
    null
}

android {
    namespace = "io.github.toolicious.labler"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.toolicious.labler"
        minSdk = 26
        targetSdk = 36
        // Version derived from four fields (Luanti/Minetest-style positional versionCode): a release
        // has build 0, so its code ends in "00"; test/beta builds count up 01..99 above it. Only the
        // four vals below change when bumping the version.
        val verMajor = 1
        val verMinor = 1
        val verPatch = 0
        val verBuild = 0 // 0 = release; 1..99 = test/beta build made since this release
        versionCode = verMajor * 1_000_000 + verMinor * 10_000 + verPatch * 100 + verBuild
        versionName = "$verMajor.$verMinor.$verPatch" +
            (if (verBuild > 0) "-dev" + verBuild.toString().padStart(2, '0') else "")
        manifestPlaceholders["appName"] = "LaBLEr"
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = signingProps.parentFile.resolve(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Separate package name + name so debug and release sit side by side in the drawer.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "LaBLEr DEBUG"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signing != null) signingConfig = signingConfigs.getByName("release")
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":printer"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.emoji2.emojipicker)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
