plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "sh.eliza.textbender"
  compileSdk = 33
  buildToolsVersion = "33.0.0"

  defaultConfig {
    applicationId = "sh.eliza.textbender"
    minSdk = 26
    targetSdk = 33
    versionCode = 1
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions { jvmTarget = "11" }
}

dependencies {
  implementation("androidx.core:core-ktx:1.9.0")
  implementation("androidx.appcompat:appcompat:1.5.1")
  implementation("com.google.android.material:material:1.7.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
  implementation("androidx.preference:preference:1.2.0")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.4")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}
