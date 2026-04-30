group = "com.alfapay.checkout_flow"
version = "0.0.1"

// Note: this plugin does NOT declare `buildscript { repositories }` or
// `allprojects { repositories }`. Modern Flutter projects use
// `RepositoriesMode.FAIL_ON_PROJECT_REPOS` in their settings.gradle
// which makes plugin-level repo declarations fatal. Consumers control
// repos via their own `dependencyResolutionManagement` — see README
// for the JitPack repo we require for the Checkout Risk SDK.

plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.alfapay.checkout_flow"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()

                it.outputs.upToDateWhen { false }

                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
}

dependencies {
    // Checkout.com mobile Flow Components — Maven Central.
    //   https://central.sonatype.com/artifact/com.checkout/checkout-android-components
    //
    // The plugin compiles against this dependency. Consumer apps must also
    // declare it themselves (see README) so it ships in the final APK.
    implementation("com.checkout:checkout-android-components:1.0.0")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.mockito:mockito-core:5.0.0")
}
