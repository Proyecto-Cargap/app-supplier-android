plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "mx.cargap.supplier"
    compileSdk = 37
    defaultConfig {
        applicationId = "mx.cargap.supplier"
        minSdk = 28; targetSdk = 37; versionCode = 1; versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "environment"
    productFlavors {
        create("development") {
            dimension = "environment"; applicationIdSuffix = ".dev"
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("cargap.apiBaseUrl").getOrElse("http://10.0.2.2:50000")}\"")
            buildConfigField("boolean", "ALLOW_LOCAL_HTTP", "true")
            resValue("string", "app_name", "CARGAP Transportistas DEV")
        }
        create("staging") {
            dimension = "environment"; applicationIdSuffix = ".test"
            buildConfigField("String", "API_BASE_URL", "\"https://api.test.cargap.mx\"")
            buildConfigField("boolean", "ALLOW_LOCAL_HTTP", "false")
            resValue("string", "app_name", "CARGAP Transportistas TEST")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://api.cargap.mx\"")
            buildConfigField("boolean", "ALLOW_LOCAL_HTTP", "false")
            resValue("string", "app_name", "CARGAP Transportistas")
        }
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    buildFeatures { compose = true; buildConfig = true; resValues = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":domain")); implementation(project(":application")); implementation(project(":data"))
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.8.0-alpha01")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.4.0-alpha01")
    androidTestImplementation("androidx.test:runner:1.8.0-alpha01")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
