package com.example.microbiologicaldetection.data

/**
 * Ficha tecnica de un equipo del laboratorio.
 *
 * Refleja las secciones que muestra la pantalla de detalle. Los campos son
 * nulos mientras no haya datos: la pantalla resuelve el estado vacio por su
 * cuenta, asi que el servidor puede devolver fichas incompletas sin romper nada.
 */
data class EquipmentSheet(
    val name: String,
    val components: String? = null,
    val procedure: String? = null,
    val ppe: String? = null,
    val risks: String? = null,
    val practices: String? = null
) {
    /**
     * Rellena los huecos de esta ficha con [other] sin pisar lo que ya tiene.
     * La informacion institucional manda; [other] (Gemini) solo completa.
     */
    fun completedWith(other: EquipmentSheet) = EquipmentSheet(
        name = name,
        components = components.orFrom(other.components),
        procedure = procedure.orFrom(other.procedure),
        ppe = ppe.orFrom(other.ppe),
        risks = risks.orFrom(other.risks),
        practices = practices.orFrom(other.practices)
    )

    private fun String?.orFrom(fallback: String?): String? =
        this?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() }
}
