import java.util.Properties
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.gigrun"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gigrun"
        minSdk = 28
        targetSdk = 36
        versionCode = 7
        versionName = "7.0"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        val mapsApiKey = System.getenv("MAPS_API_KEY")
            ?: localProperties.getProperty("MAPS_API_KEY")?.takeIf { it.isNotBlank() && it != "YOUR_API_KEY_HERE" }
            ?: ""
        if (mapsApiKey.isBlank()) {
            logger.warn("MAPS_API_KEY is missing — Maps will render blank. Set env MAPS_API_KEY or local.properties.")
        }
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    signingConfigs {
        create("release") {
            // Never commit a keystore: all four come from env/CI secrets.
            val ks = System.getenv("GIGRUN_KEYSTORE") ?: return@create
            storeFile = file(ks)
            storePassword = System.getenv("GIGRUN_STORE_PASSWORD")
            keyAlias = System.getenv("GIGRUN_KEY_ALIAS")
            keyPassword = System.getenv("GIGRUN_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")?.takeIf {
                it.storeFile?.exists() == true
            } ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      buildConfig = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Room exportSchema=true requires a schema dir (feeds future Migrations).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.service)

  // Lifecycle & ViewModel
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose UI
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Navigation Compose
  implementation(libs.androidx.navigation.compose)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Hilt Dependency Injection
  implementation(libs.hilt.android)
  ksp(libs.hilt.android.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  // DataStore Preferences
  implementation(libs.androidx.datastore.preferences)

  // WorkManager
  implementation(libs.androidx.work.runtime.ktx)

  // Play Services Location & Maps
  implementation(libs.play.services.location)
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)

  // Serialization
  implementation(libs.kotlinx.serialization.json)

  // ML Kit Text Recognition
  implementation(libs.play.services.mlkit.text.recognition)
  implementation(libs.kotlinx.coroutines.play.services)

  // Testing
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
