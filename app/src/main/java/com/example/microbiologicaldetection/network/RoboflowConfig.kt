package com.example.microbiologicaldetection.network

import com.example.microbiologicaldetection.BuildConfig

object RoboflowConfig {
    val modelUrl: String get() = BuildConfig.ROBOFLOW_MODEL_URL
    val endpoint: String get() = BuildConfig.ROBOFLOW_ENDPOINT
    val apiKey: String get() = BuildConfig.ROBOFLOW_API_KEY

    val isConfigured: Boolean
        get() = modelUrl.isNotBlank() && endpoint.isNotBlank() && apiKey.isNotBlank()
}
