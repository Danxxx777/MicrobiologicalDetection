package com.example.microbiologicaldetection.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object ChatHistory {

    private const val PREFS_NAME = "equipment_chat_history"
    private const val MAX_MESSAGES = 40

    fun load(context: Context, equipment: String): List<ChatMessage> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(keyFor(equipment), null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val text = item.optString("text").trim()
                    if (text.isNotEmpty()) {
                        add(
                            ChatMessage(
                                text = text,
                                isUser = item.optBoolean("isUser"),
                                source = item.optString("source").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, equipment: String, messages: List<ChatMessage>) {
        val persisted = messages.filterNot { it.isTyping }.takeLast(MAX_MESSAGES)
        val array = JSONArray()
        persisted.forEach { message ->
            array.put(JSONObject().apply {
                put("text", message.text)
                put("isUser", message.isUser)
                message.source?.takeIf { it.isNotBlank() }?.let { put("source", it) }
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyFor(equipment), array.toString())
            .apply()
    }

    private fun keyFor(equipment: String): String {
        val normalized = equipment.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return "conversation_${normalized.ifBlank { "general" }}"
    }
}
