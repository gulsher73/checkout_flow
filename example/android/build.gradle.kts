allprojects {
    repositories {
        google()
        mavenCentral()
        // Checkout Components → Risk SDK lives on JitPack:
        //   com.github.checkout:checkout-risk-sdk-android
        maven { url = uri("https://jitpack.io") }
        // Risk SDK depends on Fingerprint Pro which lives on Fingerprint's
        // own Maven registry, not Maven Central:
        //   com.fingerprint.android:pro
        maven { url = uri("https://maven.fpregistry.io/releases") }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
