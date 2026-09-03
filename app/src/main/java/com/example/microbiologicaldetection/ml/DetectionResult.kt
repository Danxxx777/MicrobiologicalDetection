package com.example.microbiologicaldetection.ml

import android.graphics.RectF

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val classIndex: Int
)
