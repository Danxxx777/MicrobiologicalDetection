package com.example.microbiologicaldetection.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object EquipmentHistory {
    private const val PREFS_NAME = "equipment_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 8

    data class Entry(val label: String, val confidence: Float)

    fun record(context: Context, label: String, confidence: Float) {
        if (label.isBlank()) return
        val current = getAll(context)
        if (current.firstOrNull()?.label == label) return
        val updated = listOf(Entry(label, confidence)) + current.filterNot { it.label == label }
        val json = JSONArray()
        updated.take(MAX_ITEMS).forEach { entry ->
            json.put(JSONObject().put("label", entry.label).put("confidence", entry.confidence.toDouble()))
        }
        prefs(context).edit().putString(KEY_ITEMS, json.toString()).apply()
    }

    fun getAll(context: Context): List<Entry> = runCatching {
        val json = JSONArray(prefs(context).getString(KEY_ITEMS, "[]"))
        buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                add(Entry(item.getString("label"), item.optDouble("confidence", 0.0).toFloat()))
            }
        }
    }.getOrDefault(emptyList())

    fun saveSheet(context: Context, label: String, sheet: EquipmentSheet) {
        val json = JSONObject()
            .put("name", sheet.name)
            .put("components", sheet.components)
            .put("procedure", sheet.procedure)
            .put("ppe", sheet.ppe)
            .put("risks", sheet.risks)
            .put("practices", sheet.practices)
        prefs(context).edit().putString(sheetKey(label), json.toString()).apply()
    }

    fun getSheet(context: Context, label: String): EquipmentSheet? = runCatching {
        val raw = prefs(context).getString(sheetKey(label), null) ?: return null
        val json = JSONObject(raw)
        EquipmentSheet(
            name = json.optString("name", label),
            components = json.optionalText("components"),
            procedure = json.optionalText("procedure"),
            ppe = json.optionalText("ppe"),
            risks = json.optionalText("risks"),
            practices = json.optionalText("practices")
        )
    }.getOrNull()

    private fun sheetKey(label: String) = "sheet_" + label.lowercase().replace(Regex("[^a-z0-9]+"), "_")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun JSONObject.optionalText(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }
}
