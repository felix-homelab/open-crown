plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.opencrown.app"
    compileSdk = 36
    // Pinned so AGP does not try to fetch its default build-tools at configure time.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "org.opencrown.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // arm64 only: the runtime library is built for this ABI and the phone
        // matrix is 64-bit (docs/00-charter.md section 8).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // The generated UniFFI bindings are compiled into the app. The JVM test
    // loads the host build of the same library.
    sourceSets.getByName("main").kotlin.srcDir("../ffi/generated")
}

kotlin {
    jvmToolchain(21)
}

// The supported-device list lives in catalog/ so the Python validator and the
// app read the same file. Copy it into assets at build time.
val syncSupportedDevices = tasks.register<Copy>("syncSupportedDevices") {
    from(rootProject.file("catalog/supported-devices.json"))
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") {
    dependsOn(syncSupportedDevices)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    // Android needs JNA's AAR so libjnidispatch.so is packaged; the JVM test
    // uses the plain jar, which carries the desktop natives.
    implementation("net.java.dev.jna:jna:5.17.0@aar")

    testImplementation(libs.junit)
    testImplementation(libs.jna)
}
