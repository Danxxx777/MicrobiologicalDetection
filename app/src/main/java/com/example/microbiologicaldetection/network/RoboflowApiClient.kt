package com.example.microbiologicaldetection.network

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Base64
import android.util.Log
import com.example.microbiologicaldetection.ml.DetectionResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class RoboflowApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun detect(bitmap: Bitmap): Result<List<DetectionResult>> = runCatching {
        check(RoboflowConfig.isConfigured) { "Roboflow no está configurado" }

        val inputWidth = bitmap.width
        val inputHeight = bitmap.height
        val bytes = ByteArrayOutputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "No se pudo preparar la imagen"
            }
            stream.toByteArray()
        }

        val payload = JSONObject().apply {
            put("api_key", RoboflowConfig.apiKey)
            put("inputs", JSONObject().put(
                "image",
                JSONObject()
                    .put("type", "base64")
                    .put("value", Base64.encodeToString(bytes, Base64.NO_WRAP))
            ))
        }
        val request = Request.Builder()
            .url(RoboflowConfig.endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "Roboflow respondió ${response.code}: ${errorMessage(body)}"
            }
            parseDetections(body, inputWidth, inputHeight, bitmap.width, bitmap.height)
        }
    }

    private fun parseDetections(
        body: String,
        inputWidth: Int,
        inputHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectionResult> {
        val predictions = mutableListOf<JSONObject>()
        collectPredictions(JSONObject(body), predictions)
        // Diagnostico: distingue "el modelo no vio nada" de "lo vio y lo
        // descartaron el umbral o la zona central".
        Log.d(TAG, "predicciones crudas=${predictions.size} " +
                predictions.joinToString { p ->
                    "${p.optString("class")}:${p.optDouble("confidence", 0.0)}"
                })

        val scaleX = imageWidth.toFloat() / inputWidth
        val scaleY = imageHeight.toFloat() / inputHeight
        val targetZone = RectF(
            imageWidth * 0.35f,
            imageHeight * 0.35f,
            imageWidth * 0.65f,
            imageHeight * 0.65f
        )

        return predictions.mapNotNull { prediction ->
            val confidence = prediction.optDouble("confidence", 0.0).toFloat()
                .let { if (it > 1f) it / 100f else it }
            if (confidence < MIN_CONFIDENCE) return@mapNotNull null

            val centerX = prediction.optDouble("x", Double.NaN).toFloat() * scaleX
            val centerY = prediction.optDouble("y", Double.NaN).toFloat() * scaleY
            val width = prediction.optDouble("width", Double.NaN).toFloat() * scaleX
            val height = prediction.optDouble("height", Double.NaN).toFloat() * scaleY
            if (!centerX.isFinite() || !centerY.isFinite() || !width.isFinite() || !height.isFinite()) {
                return@mapNotNull null
            }

            val box = RectF(
                (centerX - width / 2f).coerceIn(0f, imageWidth.toFloat()),
                (centerY - height / 2f).coerceIn(0f, imageHeight.toFloat()),
                (centerX + width / 2f).coerceIn(0f, imageWidth.toFloat()),
                (centerY + height / 2f).coerceIn(0f, imageHeight.toFloat())
            )
            if (box.width() <= 0f || box.height() <= 0f || !RectF.intersects(box, targetZone)) {
                return@mapNotNull null
            }

            DetectionResult(
                label = prediction.optString("class", prediction.optString("class_name", "Equipo")),
                confidence = confidence,
                boundingBox = box,
                classIndex = prediction.optInt("class_id", 0)
            )
        }.also { kept ->
            Log.d(TAG, "tras umbral ${MIN_CONFIDENCE} y zona central quedan=${kept.size}")
        }.maxByOrNull { it.confidence }?.let(::listOf).orEmpty()
    }

    private fun collectPredictions(value: Any?, result: MutableList<JSONObject>) {
        when (value) {
            is JSONObject -> {
                if (value.has("x") && value.has("y") && value.has("width") && value.has("height")) {
                    result += value
                    return
                }
                value.keys().forEach { key -> collectPredictions(value.opt(key), result) }
            }
            is JSONArray -> for (index in 0 until value.length()) {
                collectPredictions(value.opt(index), result)
            }
        }
    }

    private fun errorMessage(body: String): String = runCatching {
        JSONObject(body).optString("detail").ifBlank { body.take(200) }
    }.getOrDefault(body.take(200))

    companion object {
        private const val TAG = "Roboflow"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val MIN_CONFIDENCE = 0.45f
    }
}
