import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.orbittv"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.orbittv"
        // Fire OS 6 (API 25) and up. The reference TV (see README) runs Fire OS 7 / API 28.
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // The target TV is 32-bit ARM. Any native library must ship this ABI.
        ndk { abiFilters += listOf("armeabi-v7a") }
        // TMDB personal key from local.properties (git-ignored); empty when absent, and the catalogue then stays empty.
        val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        buildConfigField("String", "TMDB_API_KEY", "\"" + (local.getProperty("tmdb.apiKey") ?: "") + "\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"" + (local.getProperty("youtube.apiKey") ?: "") + "\"")
        // Contact named in the User-Agent of feed requests (Wikimedia asks for one): contact.email from local.properties, else the project page.
        val contact = local.getProperty("contact.email")?.trim()?.takeIf { it.isNotEmpty() } ?: "https://github.com/aika-labs-design/orbit-tv"
        buildConfigField("String", "CONTACT", "\"" + contact.replace("\\", "").replace("\"", "") + "\"")
    }
    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // android.util.Log and friends answer defaults in JVM tests, so classes that log (WebBridge) can be tested.
    testOptions { unitTests.isReturnDefaultValues = true }

    // An optional git-ignored seed (repo root local-profiles.json), preloaded as the profiles on first launch; shipped only when it exists.
    val localAssets = layout.buildDirectory.dir("local-assets").get().asFile
    localAssets.deleteRecursively()
    rootProject.file("local-profiles.json").takeIf { it.isFile }?.let { it.copyTo(File(localAssets, "local-profiles.json")) }
    sourceSets["main"].assets.srcDir(localAssets)

}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.orgjson)
}
