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
