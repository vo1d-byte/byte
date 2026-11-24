plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.byte.ide"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.byte.ide"
        minSdk = 26
        // API 28 on purpose: Android 10+ blocks exec() of files in the app's data
        // directory for apps targeting 29+. Byte must run downloaded toolchains.
        // This means sideloading (F-Droid / GitHub releases), not Google Play.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

// Build the Rust library with cargo-ndk before packaging. Skip with -PskipCargo.
val cargoNdk = tasks.register<Exec>("cargoNdk") {
    onlyIf { !project.hasProperty("skipCargo") }
    workingDir = rootProject.projectDir.parentFile
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a", "-t", "x86_64",
        "-o", "android/app/src/main/jniLibs",
        "build", "--release", "-p", "byte-jni",
    )
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(cargoNdk) }
