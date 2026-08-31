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
        versionCode = 7
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Credentials are injected via GitHub Secrets (or local env vars).
            // Required env vars:
            //   KEYSTORE_FILE      - absolute path to the JKS keystore file
            //   KEYSTORE_PASSWORD  - keystore/store password
            //   KEY_ALIAS          - key alias (key0)
            //   KEY_PASSWORD       - key password
            val keystorePath = System.getenv("KEYSTORE_FILE")
            val keystorePass = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasVal  = System.getenv("KEY_ALIAS")
            val keyPassVal   = System.getenv("KEY_PASSWORD")

            if (keystorePath != null && keystorePass != null && keyAliasVal != null && keyPassVal != null) {
                storeFile     = file(keystorePath)
                storePassword = keystorePass
                keyAlias      = keyAliasVal
                keyPassword   = keyPassVal
                storeType     = "JKS"  // explicit JKS avoids PKCS12 ASN.1 compat issues
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
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