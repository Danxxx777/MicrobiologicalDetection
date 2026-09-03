package com.example.microbiologicaldetection.data

import android.content.Context

object EquipmentSession {
    private const val PREFS_NAME = "equipment_session"
    private const val KEY_EQUIPMENT = "last_equipment"

    fun save(context: Context, equipment: String) {
        if (equipment.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EQUIPMENT, equipment)
            .apply()
    }

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EQUIPMENT, "")
            .orEmpty()
}
