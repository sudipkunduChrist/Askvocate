import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.example.askvocate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.askvocate"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Helper to resolve secret from environment or local config files (.env, local.properties)
        fun getSecret(key: String): String {
            System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }

            val candidateFiles = listOf(
                project.file(".env"),
                rootProject.file("app/.env"),
                rootProject.file(".env"),
                rootProject.file("backend/.env"),
                rootProject.file("local.properties"),
                project.file("local.properties")
            )

            for (f in candidateFiles) {
                if (f.exists()) {
                    val props = Properties().apply { f.inputStream().use { load(it) } }
                    props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                }
            }

            return ""
        }

        // same value as GOOGLE_WEB_CLIENT_ID in backend/.env.
        val webClientId = getSecret("GOOGLE_CLIENT_ID").ifEmpty { getSecret("GOOGLE_WEB_CLIENT_ID") }
        println("[Askvocate Build] GOOGLE_WEB_CLIENT_ID = " + (if (webClientId.isNotBlank()) webClientId else "<NOT CONFIGURED>"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Layout
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.swiperefreshlayout)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Fragment & Activity KTX
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)

    // Image Loading
    implementation(libs.coil)

    // Circle ImageView
    implementation(libs.circleimageview)

    // Lottie Animations
    implementation(libs.lottie)

    // Google Sign-In (Credential Manager + Google ID)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Add these tasks if the IDE continues to report them as missing during sync.
// They were likely added to fix a previous sync issue and can be removed once the IDE cache is cleared.
tasks.register("prepareKotlinBuildScriptModel") {
    doLast { println("Kotlin build script model prepared") }
}
tasks.register("prepareKotlinBuildScriptMode") {
    doLast { println("Kotlin build script model prepared") }
}
