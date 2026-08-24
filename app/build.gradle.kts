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
        versionCode = 6
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("pocketsqlkey")
            val pass = charArrayOf('R', 'a', 'd', 'h', 'a', 'R', 'a', 'n', 'i', '@', '1', '2', '3').concatToString()
            storePassword = pass
            keyAlias = "key0"
            keyPassword = pass
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            ndk {
                debugSymbolLevel = "FULL"
            }
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
    implementation(libs.security.crypto)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}