plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

// Signing secrets are supplied only to the local build process, never committed.
val releaseStorePath = providers.environmentVariable("LOCKALARM_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("LOCKALARM_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("LOCKALARM_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LOCKALARM_KEY_PASSWORD").orNull
val releaseSigningReady = listOf(
    releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { !it.isNullOrBlank() }

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Reject release packaging without an explicit private signing key."
    doLast {
        check(releaseSigningReady) {
            "Release signing is not configured. Run tools/release.ps1 -Action Build; never put passwords in source or command arguments."
        }
        val store = rootProject.file(requireNotNull(releaseStorePath))
        check(store.isFile && !store.name.equals("debug.keystore", ignoreCase = true)) {
            "An existing non-debug release keystore is required."
        }
    }
}

tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "packageRelease", "packageReleaseBundle", "signReleaseBundle", "validateSigningRelease")) {
        dependsOn(verifyReleaseSigning)
    }
}

android {
    namespace = "com.routinealarm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.routinealarm.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 37
        versionName = "0.15.22"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = rootProject.file(requireNotNull(releaseStorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    val roomVersion = "2.8.4"

    // Last stable line compatible with AGP 8.11 / compileSdk 36.
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
