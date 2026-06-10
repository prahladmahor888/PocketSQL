plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mysql.pocketsql"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.mysql.pocketsql"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.all {
            (it as org.gradle.api.tasks.testing.Test).maxHeapSize = "256m"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.bouncycastle)
    implementation(libs.bcpkix)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)
    implementation(libs.play.integrity)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.bouncycastle)
    testImplementation(libs.bcpkix)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}