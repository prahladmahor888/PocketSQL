import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

fun loadEnvProperty(key: String): String? {
    // 1. System Environment Variable
    val envVal = System.getenv(key)
    if (!envVal.isNullOrBlank()) return envVal

    // 2. Gradle / System property
    val sysProp = System.getProperty(key)
    if (!sysProp.isNullOrBlank()) return sysProp

    // 3. Project Root .env File
    val rootDotEnv = rootProject.file(".env")
    if (rootDotEnv.exists()) {
        try {
            val lines = rootDotEnv.readLines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val k = trimmed.substring(0, eqIdx).trim()
                    var v = trimmed.substring(eqIdx + 1).trim()
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length - 1)
                    }
                    if (k == key && v.isNotBlank()) {
                        return v
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // 4. Module .env File (app/.env)
    val moduleDotEnv = project.file(".env")
    if (moduleDotEnv.exists()) {
        try {
            val lines = moduleDotEnv.readLines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val k = trimmed.substring(0, eqIdx).trim()
                    var v = trimmed.substring(eqIdx + 1).trim()
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length - 1)
                    }
                    if (k == key && v.isNotBlank()) {
                        return v
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // 5. local.properties Fallback
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        try {
            val props = Properties()
            localPropsFile.inputStream().use { props.load(it) }
            val propVal = props.getProperty(key)
            if (!propVal.isNullOrBlank()) return propVal
        } catch (_: Exception) {}
    }

    return null
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
        versionCode = 9
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Load signing credentials automatically from .env, environment variables, or local.properties
            val keystorePath = loadEnvProperty("KEYSTORE_FILE") ?: loadEnvProperty("RELEASE_STORE_FILE")
            val keystorePass = loadEnvProperty("KEYSTORE_PASSWORD") ?: loadEnvProperty("RELEASE_STORE_PASSWORD")
            val keyAliasVal  = loadEnvProperty("KEY_ALIAS") ?: loadEnvProperty("RELEASE_KEY_ALIAS")
            val keyPassVal   = loadEnvProperty("KEY_PASSWORD") ?: loadEnvProperty("RELEASE_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() && !keystorePass.isNullOrBlank() && !keyAliasVal.isNullOrBlank() && !keyPassVal.isNullOrBlank()) {
                val kFile = if (File(keystorePath).isAbsolute) File(keystorePath) else rootProject.file(keystorePath)
                if (kFile.exists()) {
                    storeFile     = kFile
                    storePassword = keystorePass
                    keyAlias      = keyAliasVal
                    keyPassword   = keyPassVal
                    enableV2Signing = true
                    enableV3Signing = true
                    enableV4Signing = true
                }
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