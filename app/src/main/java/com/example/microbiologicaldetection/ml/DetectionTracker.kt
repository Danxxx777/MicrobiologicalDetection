package com.example.microbiologicaldetection.ml

import android.graphics.RectF
import android.os.SystemClock

/**
 * Da continuidad a las detecciones entre inferencias.
 *
 * El detector trabaja por fotogramas sueltos: basta con que el modelo falle una
 * vez, que la petición a Roboflow no responda o que la mano se mueva, para que
 * la lista llegue vacía y la vista borre todo. Este tracker se interpone entre
 * el detector y el overlay y hace dos cosas:
 *
 *  - **Retención** (solo cuando el fotograma llega vacío): la última detección
 *    conocida sobrevive [holdMs] en su sitio. Si el modelo sí devuelve algo,
 *    ese resultado manda y no se arrastran fantasmas de otras etiquetas.
 *    Si el equipo reaparece dentro de la ventana, para la vista nunca se
 *    fue; si de verdad salió de cuadro, se cae sola.
 *  - **Suavizado**: la caja no salta a la nueva posición, se acerca a ella
 *    ([follow] por actualización), de modo que el HUD se desplaza en vez de
 *    parpadear de un sitio a otro.
 *
 * Las detecciones se emparejan por etiqueta y solapamiento (IoU). Si de una
 * etiqueta solo hay un candidato se acepta aunque no solape: es el caso de un
 * movimiento brusco, donde la caja nueva y la vieja ya no se tocan pero
 * evidentemente son el mismo equipo.
 */
class DetectionTracker(
    private val holdMs: Long = DEFAULT_HOLD_MS,
    private val follow: Float = DEFAULT_FOLLOW,
    private val iouThreshold: Float = DEFAULT_IOU
) {

    private class Track(
        val label: String,
        val classIndex: Int,
        val box: RectF,
        var confidence: Float,
        var lastSeenAt: Long
    )

    private val tracks = mutableListOf<Track>()

    /** Se llama al cambiar de cámara o al reanudar: el historial ya no sirve. */
    fun reset() {
        tracks.clear()
    }

    /**
     * Entrega lo que debe verse ahora mismo: las detecciones de este fotograma
     * más las que siguen dentro de su ventana de retención.
     */
    fun update(
        detections: List<DetectionResult>,
        now: Long = SystemClock.elapsedRealtime()
    ): List<DetectionResult> {
        val pending = tracks.toMutableList()

        for (detection in detections) {
            val candidates = pending.filter { it.label == detection.label }
            val best = candidates.maxByOrNull { iou(it.box, detection.boundingBox) }
            val matched = best != null &&
                    (candidates.size == 1 || iou(best.box, detection.boundingBox) >= iouThreshold)

            if (matched && best != null) {
                approach(best.box, detection.boundingBox)
                best.confidence = detection.confidence
                best.lastSeenAt = now
                pending.remove(best)
            } else {
                tracks.add(
                    Track(
                        label = detection.label,
                        classIndex = detection.classIndex,
                        box = RectF(detection.boundingBox),
                        confidence = detection.confidence,
                        lastSeenAt = now
                    )
                )
            }
        }

        if (detections.isEmpty()) {
            // Fotograma sin resultados: es justo lo que hay que absorber. Se
            // conserva la ultima posicion conocida hasta agotar la espera.
            tracks.removeAll { now - it.lastSeenAt > holdMs }
        } else {
            // El modelo si vio algo: este fotograma manda y lo que no aparece
            // en el se descarta. Retener aqui dejaria fantasmas de otras
            // etiquetas conviviendo con la deteccion buena, y el HUD solo se
            // abre cuando hay una sola.
            tracks.removeAll { it.lastSeenAt != now }
        }

        return tracks
            .sortedByDescending { it.confidence }
            .map {
                DetectionResult(
                    label = it.label,
                    confidence = it.confidence,
                    boundingBox = RectF(it.box),
                    classIndex = it.classIndex
                )
            }
    }

    /** Acerca [box] hacia [target] sin llegar de golpe. */
    private fun approach(box: RectF, target: RectF) {
        box.left += (target.left - box.left) * follow
        box.top += (target.top - box.top) * follow
        box.right += (target.right - box.right) * follow
        box.bottom += (target.bottom - box.bottom) * follow
    }

    private fun iou(a: RectF, b: RectF): Float {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val intersection = w * h
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    companion object {
        /** Algo más del doble del intervalo remoto: aguanta dos inferencias perdidas. */
        private const val DEFAULT_HOLD_MS = 1200L

        /** 1 = sigue exacto y salta; más bajo = más suave pero con retraso. */
        private const val DEFAULT_FOLLOW = 0.6f

        private const val DEFAULT_IOU = 0.2f
    }
}
