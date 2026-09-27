plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.relay.chat"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.relay.chat"
        minSdk = 26
        // 28 on purpose: newer targets forbid exec() of files in app storage (W^X), which the
        // bundled Linux runtime for Claude Code / Codex needs. Sideload-only, like Termux.
        targetSdk = 28
        // Overridable by CI (-PrelayVersionCode/-PrelayVersionName) so a tagged release's APK
        // reports its actual version instead of always "1.0".
        versionCode = (project.findProperty("relayVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("relayVersionName") as String?) ?: "1.0"
    }

    signingConfigs {
        getByName("debug") {
            // A fixed keystore (gitignored; CI restores it from a repo secret), not the
            // ambient per-machine ~/.android/debug.keystore: every build (local or CI) must
            // sign with the same key, or users can't install an update over a previous one
            // — see keystore/README.md.
            storeFile = rootProject.file("keystore/relay-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK is installable as-is.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // targetSdk 28 is deliberate (see defaultConfig): Relay is sideloaded, never on Play.
        disable += "ExpiredTargetSdkVersion"
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
