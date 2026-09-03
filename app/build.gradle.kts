import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val openAiApiKey = localProperties.getProperty("OPENAI_API_KEY", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val openAiBaseUrl = localProperties.getProperty("OPENAI_BASE_URL", "https://api.openai.com")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val openAiEndpoint = localProperties.getProperty("OPENAI_ENDPOINT", "/v1/responses")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val openAiModel = localProperties.getProperty("OPENAI_MODEL", "gpt-5.6-luna")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val roboflowApiKey = localProperties.getProperty("ROBOFLOW_API_KEY", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val roboflowModelUrl = localProperties.getProperty("ROBOFLOW_MODEL_URL", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val roboflowEndpoint = localProperties.getProperty("ROBOFLOW_ENDPOINT", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.example.microbiologicaldetection"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.microbiologicaldetection"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")
        buildConfigField("String", "OPENAI_BASE_URL", "\"$openAiBaseUrl\"")
        buildConfigField("String", "OPENAI_ENDPOINT", "\"$openAiEndpoint\"")
        buildConfigField("String", "OPENAI_MODEL", "\"$openAiModel\"")
        buildConfigField("String", "ROBOFLOW_API_KEY", "\"$roboflowApiKey\"")
        buildConfigField("String", "ROBOFLOW_MODEL_URL", "\"$roboflowModelUrl\"")
        buildConfigField("String", "ROBOFLOW_ENDPOINT", "\"$roboflowEndpoint\"")
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // LiteRT (antes TensorFlow Lite)
    implementation(libs.litert)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // HTTP
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
