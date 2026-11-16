import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
  if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { load(it) }
  }
}
val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
val appVersionName = versionProps.getProperty("VERSION_NAME", "1.0.0")

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
  if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { load(it) }
  }
}

val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
  ?: keystoreProps.getProperty("storeFile")
val releaseStoreFile = releaseStorePath
  ?.trim()
  ?.takeIf { it.isNotEmpty() }
  ?.let { path ->
    val candidate = file(path)
    if (candidate.exists()) candidate else rootProject.file(path)
  }
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
  ?: keystoreProps.getProperty("storePassword")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
  ?: keystoreProps.getProperty("keyAlias")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
  ?: keystoreProps.getProperty("keyPassword")

val hasReleaseSigning = releaseStoreFile != null &&
  releaseStoreFile.exists() &&
  releaseStoreFile.isFile &&
  !releaseStorePassword.isNullOrBlank() &&
  !releaseKeyAlias.isNullOrBlank() &&
  !releaseKeyPassword.isNullOrBlank()

android {
  namespace = "com.whatsapprepeater.app"
  compileSdk = 36

  defaultConfig {
    // Identidad fija del paquete: nunca cambiar entre versiones para conservar firma y datos locales.
    applicationId = "com.whatsapprepeater.android"
    minSdk = 28
    targetSdk = 35
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = releaseStoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
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

tasks.configureEach {
  if (name == "assembleRelease" || name == "bundleRelease") {
    doFirst {
      if (!hasReleaseSigning) {
        throw GradleException(
          "No se encontró la clave de firma de producción. Configura ANDROID_KEYSTORE_* o keystore.properties. Nunca publiques una versión firmada con clave de depuración.",
        )
      }
    }
  }
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:automation"))
  implementation(project(":core:data"))
  implementation(project(":core:updater"))

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.room.runtime)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
