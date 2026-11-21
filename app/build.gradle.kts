plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

val baseVersionName = "0.0.2-beta.1"
val ciBuildNumber = providers.gradleProperty("ciBuildNumber").map(String::toInt).orNull
val isCiBetaBuild = providers.gradleProperty("ciBetaBuild").map(String::toBoolean).getOrElse(false)

android {
    namespace = "cn.yixin.todo"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.yixin.todo"
        minSdk = 26
        targetSdk = 35
        versionCode = ciBuildNumber?.let { 1000 + it } ?: 4
        versionName = if (isCiBetaBuild && ciBuildNumber != null) "$baseVersionName-build.$ciBuildNumber" else baseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":data"))
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
