package com.example.microbiologicaldetection.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ChatResponse(
        val answer: String,
        val source: String? = null
    )

    suspend fun sendMessage(serverUrl: String, equipment: String, message: String): Result<ChatResponse> =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("equipment", equipment)
                    put("message", message)
                }.toString()

                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$serverUrl/chat")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResp = JSONObject(responseBody)
                    val answer = jsonResp.optString("answer", jsonResp.optString("response", responseBody))
                    val source = jsonResp.optString("source", "").takeIf { it.isNotBlank() }
                    Result.success(ChatResponse(answer = answer, source = source))
                } else {
                    Result.failure(Exception("Error ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
