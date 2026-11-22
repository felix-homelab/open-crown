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
        // UniFFI 0.32 emits java.lang.ref.Cleaner (API 33). Declaring 31 would
        // promise a runtime the binding cannot provide.
        minSdk = 33
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
        debug {
            buildConfigField("boolean", "PRIVILEGED_CHAIN", "true")
            // Research-only privileged chain. Never enabled for release.
            externalNativeBuild {
                cmake {
                    arguments += "-DOPEN_CROWN_BUILD_RELAY=ON"
                }
            }
        }
        release {
            buildConfigField("boolean", "PRIVILEGED_CHAIN", "false")
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
        buildConfig = true
    }

    // UniFFI Kotlin bindings are generated from the Rust surface and committed.
    // They live outside app/ so the Rust and Kotlin sides have one source file.
    sourceSets {
        getByName("main") {
            java.srcDir(rootProject.file("ffi/generated"))
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Payload packages are the source of truth. The app assets directory is
// generated and gitignored.
val syncPayloadAssets = tasks.register<Copy>("syncPayloadAssets") {
    from(rootProject.file("payloads")) {
        include("org.opencrown.payload.pagecache-relay/**")
    }
    // Debug-only: the quarantined payload must not be packaged in release.
    into(layout.projectDirectory.dir("src/debug/assets/payloads"))
}

// The registry is authored in docs/ and validated by its generator. Copying it
// at build time keeps the app's Evidence screen and the docs from drifting.
val syncFeatureRegistry = tasks.register<Copy>("syncFeatureRegistry") {
    from(rootProject.file("docs/cleanroom")) {
        include("feature-registry.json")
    }
    into(layout.projectDirectory.dir("src/main/assets/cleanroom"))
}

tasks.named("preBuild") {
    dependsOn(syncPayloadAssets, syncFeatureRegistry)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    testImplementation(libs.junit)
    testImplementation(libs.json)
}