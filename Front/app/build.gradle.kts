plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.replan"
    // compileSdk 버전을 최신 37로 수정했습니다.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.replan"
        minSdk = 26
        // targetSdk 버전도 37로 함께 맞춰줍니다.
        targetSdk = 37
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("com.google.code.gson:gson:2.10.1")
    // 🌐 Retrofit2 & Gson Converter
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // 🪵 OkHttp & Logging Interceptor
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

}