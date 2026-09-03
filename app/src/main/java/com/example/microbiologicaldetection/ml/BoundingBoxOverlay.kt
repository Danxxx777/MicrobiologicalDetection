package com.example.microbiologicaldetection.ml

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.microbiologicaldetection.BuildConfig
import com.example.microbiologicaldetection.R
import com.example.microbiologicaldetection.data.EquipmentKnowledge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Visor de detecciones.
 *
 * Modo normal  → esquinas tipo escáner sobre cada equipo detectado.
 * Modo enfoque → escáner circular calcado del de la Pokédex de Cobblemon:
 *                banda de marcas radiales entre dos circunferencias girando en
 *                sentidos opuestos, arcos de barrido más brillantes, glow cian
 *                y paneles claros translúcidos unidos al anillo por líneas guía
 *                con codo, que entran escalonados.
 *
 * Toda la geometría va en dp: el HUD se ve igual en cualquier densidad.
 */
class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)
    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private val cyan = color(R.color.hud_cyan)
    private val cyanSoft = color(R.color.hud_cyan_soft)
    private val panelFill = color(R.color.hud_panel)
    private val panelFillAction = color(R.color.hud_panel_action)
    private val textShadow = color(R.color.hud_text_shadow)

    private val boxColors = intArrayOf(
        color(R.color.bbox_0), color(R.color.bbox_1), color(R.color.bbox_2),
        color(R.color.bbox_3), color(R.color.bbox_4), color(R.color.bbox_5),
        color(R.color.bbox_6), color(R.color.bbox_7), color(R.color.bbox_8),
        color(R.color.bbox_9), color(R.color.bbox_10), color(R.color.bbox_11),
    )

    // ── Detecciones en reposo ─────────────────────────────────────────────────
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val chipTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = sp(11f); isFakeBoldText = true
    }

    // ── HUD ───────────────────────────────────────────────────────────────────
    /** Un solo pincel reutilizado para trazar con halo (3 pasadas). */
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT
    }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hudLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = sp(10f); textAlign = Paint.Align.CENTER
        setShadowLayer(dp(2f), 0f, dp(1f), textShadow)
    }
    private val hudValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = sp(15f); isFakeBoldText = true; textAlign = Paint.Align.CENTER
        setShadowLayer(dp(2f), 0f, dp(1f), textShadow)
    }

    // ── Estado ────────────────────────────────────────────────────────────────
    var results: List<DetectionResult> = emptyList()
        set(value) {
            field = value
            val target = value.singleOrNull()
            val current = focused
            if (current != null) {
                if (target == null) {
                    dismiss()
                    return
                }
                if (target.label == current.label) {
                    updateFocusedGeometry(target, scaledBox(target.boundingBox))
                    invalidate()
                    return
                }
                dismiss()
            }

            if (target == null) {
                invalidate()
            } else if (width > 0 && height > 0) {
                focus(target, scaledBox(target.boundingBox))
            } else {
                invalidate()
            }
        }

    /** Espacio inferior ocupado por la barra de navegación de la app. */
    var bottomReserved: Float = 0f
        set(value) { field = value; invalidate() }

    var onDetailTapped: ((DetectionResult) -> Unit)? = null
    var onChatTapped: ((DetectionResult) -> Unit)? = null
    var onScannerOpened: (() -> Unit)? = null
    var onScannerDismissed: (() -> Unit)? = null

    // PreviewView usa FILL_CENTER: misma transformación aquí o las cajas se
    // descuadran en cuanto el celular tiene otra relación de aspecto.
    private var imgScale = 1f
    private var imgOffsetX = 0f
    private var imgOffsetY = 0f
    private var srcW = 0
    private var srcH = 0

    private var focused: DetectionResult? = null
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var entrance = 0f      // 0 = cerrado, 1 = desplegado
    private var rotation = 0f      // giro continuo de las coronas

    private var actionRect = RectF()

    // Las marcas se construyen una vez por radio y luego solo se rota el canvas.
    private var dialRadius = -1f
    private var dialCenterX = Float.NaN
    private var dialCenterY = Float.NaN
    private val outerTicks = Path()
    private val innerTicks = Path()

    private val entranceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 640
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { entrance = it.animatedValue as Float; invalidate() }
    }

    private val spinAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 11000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { rotation = it.animatedValue as Float; invalidate() }
    }

    fun setImageDimensions(imgW: Int, imgH: Int) {
        srcW = imgW; srcH = imgH
        recomputeTransform()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeTransform()
    }

    private fun recomputeTransform() {
        val vw = width.toFloat(); val vh = height.toFloat()
        if (vw == 0f || vh == 0f || srcW == 0 || srcH == 0) return
        imgScale = maxOf(vw / srcW, vh / srcH)
        imgOffsetX = (vw - srcW * imgScale) / 2f
        imgOffsetY = (vh - srcH * imgScale) / 2f
    }

    private fun scaledBox(box: RectF) = RectF(
        imgOffsetX + box.left * imgScale, imgOffsetY + box.top * imgScale,
        imgOffsetX + box.right * imgScale, imgOffsetY + box.bottom * imgScale
    )

    // ── Interacción ───────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val tx = event.x; val ty = event.y

        val current = focused
        if (current != null) {
            if (actionRect.contains(tx, ty)) onDetailTapped?.invoke(current)
            dismiss()
            performClick()
            return true
        }

        for (result in results.reversed()) {
            val box = scaledBox(result.boundingBox)
            if (box.contains(tx, ty)) { focus(result, box); performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun focus(result: DetectionResult, box: RectF) {
        updateFocusedGeometry(result, box)
        onScannerOpened?.invoke()
        entranceAnimator.start()
        spinAnimator.start()
        invalidate()
    }

    private fun updateFocusedGeometry(result: DetectionResult, box: RectF) {
        val alreadyFocused = focused != null
        focused = result
        val targetCx = box.centerX()
        val targetCy = box.centerY()
        val maxR = min(dp(120f), min(width, height) * 0.24f)
        val minR = min(dp(48f), maxR)
        val targetRadius = (min(box.width(), box.height()) * 0.42f).coerceIn(minR, maxR)
        if (alreadyFocused) {
            cx += (targetCx - cx) * TRACKING_SMOOTHING
            cy += (targetCy - cy) * TRACKING_SMOOTHING
            radius += (targetRadius - radius) * TRACKING_SMOOTHING
        } else {
            cx = targetCx
            cy = targetCy
            radius = targetRadius
        }
    }

    /**
     * Dispara el escáner con un resultado ficticio para poder revisar el HUD
     * sin depender de que el modelo detecte algo. Solo en compilaciones debug.
     */
    fun debugFocus(label: String, confidence: Float) {
        if (!BuildConfig.DEBUG) return
        val w = width.toFloat(); val h = height.toFloat()
        focus(
            DetectionResult(label, confidence, RectF(0f, 0f, 1f, 1f), 3),
            RectF(w * 0.34f, h * 0.32f, w * 0.66f, h * 0.50f)
        )
    }

    private fun dismiss() {
        focused = null
        entranceAnimator.cancel()
        spinAnimator.cancel()
        entrance = 0f
        onScannerDismissed?.invoke()
        invalidate()
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = focused
        if (current != null) drawScanner(canvas, current) else drawDetections(canvas)
    }

    private fun drawBrackets(canvas: Canvas, box: RectF, color: Int, weight: Float) {
        bracketPaint.color = color
        bracketPaint.strokeWidth = weight
        val armX = (box.width() * 0.25f).coerceIn(dp(10f), dp(40f))
        val armY = (box.height() * 0.25f).coerceIn(dp(10f), dp(40f))
        val r = dp(6f)

        fun corner(x: Float, y: Float, sx: Int, sy: Int) {
            val path = Path()
            path.moveTo(x + sx * armX, y)
            path.lineTo(x + sx * r, y)
            path.quadTo(x, y, x, y + sy * r)
            path.lineTo(x, y + sy * armY)
            canvas.drawPath(path, bracketPaint)
        }
        corner(box.left, box.top, 1, 1)
        corner(box.right, box.top, -1, 1)
        corner(box.left, box.bottom, 1, -1)
        corner(box.right, box.bottom, -1, -1)
    }

    private fun drawDetections(canvas: Canvas) {
        val padH = dp(8f); val padV = dp(4f); val rr = dp(8f)
        for (result in results) {
            val color = boxColors[result.classIndex % boxColors.size]
            val box = scaledBox(result.boundingBox)
            drawDetectionOutline(canvas, box, color)

            val text = "${EquipmentKnowledge.displayName(result.label)}  ${(result.confidence * 100).toInt()}%"
            val tw = chipTextPaint.measureText(text)
            val chipW = tw + padH * 2
            val chipH = chipTextPaint.fontSpacing + padV * 2
            val left = box.left.coerceIn(0f, (width - chipW).coerceAtLeast(0f))
            val top = (box.top - chipH - dp(6f)).coerceAtLeast(0f)
            chipPaint.color = color
            canvas.drawRoundRect(RectF(left, top, left + chipW, top + chipH), rr, rr, chipPaint)
            canvas.drawText(text, left + padH, top + padV - chipTextPaint.ascent(), chipTextPaint)
        }
    }

    private fun drawDetectionOutline(canvas: Canvas, box: RectF, color: Int) {
        strokePaint.color = color
        strokePaint.alpha = 70
        strokePaint.strokeWidth = dp(7f)
        canvas.drawRoundRect(box, dp(8f), dp(8f), strokePaint)

        strokePaint.alpha = 255
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(box, dp(8f), dp(8f), strokePaint)
        drawBrackets(canvas, box, color, dp(3.5f))
    }

    // ══ Escáner ═══════════════════════════════════════════════════════════════

    /**
     * Traza con halo: tres pasadas del mismo trazo, de gruesa y tenue a fina y
     * brillante. Es la forma barata de imitar el bloom del mod sin BlurMaskFilter,
     * que no está acelerado por hardware.
     */
    private fun strokeGlow(width: Float, alpha: Int, draw: (Paint) -> Unit) {
        strokePaint.color = cyanSoft
        strokePaint.strokeWidth = width * 3.2f
        strokePaint.alpha = (alpha * 0.16f).toInt()
        draw(strokePaint)

        strokePaint.strokeWidth = width * 1.9f
        strokePaint.alpha = (alpha * 0.32f).toInt()
        draw(strokePaint)

        strokePaint.color = cyan
        strokePaint.strokeWidth = width
        strokePaint.alpha = alpha
        draw(strokePaint)
    }

    private fun drawScanner(canvas: Canvas, result: DetectionResult) {
        val w = width.toFloat(); val h = height.toFloat()
        val grow = (entrance / 0.55f).coerceAtMost(1f)
        val r = radius * (0.6f + 0.4f * grow)

        drawVignette(canvas, w, h, r)
        drawDials(canvas, r, grow)
        drawPanels(canvas, result, r)
    }

    /**
     * En el mod no se oscurece el mundo. Sobre una cámara real hace falta algo
     * de contraste o el cian se pierde, así que se aplica una viñeta muy suave
     * y de caída larga: apenas se nota, pero el HUD se despega del fondo.
     */
    private fun drawVignette(canvas: Canvas, w: Float, h: Float, r: Float) {
        val outer = maxOf(w, h)
        vignettePaint.shader = RadialGradient(
            cx, cy, outer,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, VIGNETTE_COLOR),
            floatArrayOf(0f, (r * 1.1f) / outer, 1f),
            Shader.TileMode.CLAMP
        )
        vignettePaint.alpha = (70 * entrance).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    /** Banda de marcas radiales entre dos circunferencias, como el dial del mod. */
    private fun buildDials(r: Float) {
        if (dialRadius == r && dialCenterX == cx && dialCenterY == cy) return
        dialRadius = r
        dialCenterX = cx
        dialCenterY = cy

        outerTicks.reset()
        val outerFrom = r
        for (i in 0 until OUTER_TICKS) {
            val rad = Math.toRadians((i * 360.0 / OUTER_TICKS))
            val len = if (i % 6 == 0) dp(13f) else dp(8f)
            val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
            outerTicks.moveTo(cx + c * outerFrom, cy + s * outerFrom)
            outerTicks.lineTo(cx + c * (outerFrom - len), cy + s * (outerFrom - len))
        }

        innerTicks.reset()
        val innerFrom = r * 0.74f
        for (i in 0 until INNER_TICKS) {
            val rad = Math.toRadians((i * 360.0 / INNER_TICKS))
            val len = if (i % 4 == 0) dp(9f) else dp(5f)
            val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
            innerTicks.moveTo(cx + c * innerFrom, cy + s * innerFrom)
            innerTicks.lineTo(cx + c * (innerFrom - len), cy + s * (innerFrom - len))
        }
    }

    private fun drawDials(canvas: Canvas, r: Float, grow: Float) {
        buildDials(r)
        val alpha = (255 * grow).toInt().coerceIn(0, 255)
        val innerR = r * 0.74f

        // Circunferencias que encierran cada banda
        strokeGlow(dp(1.6f), (alpha * 0.85f).toInt()) { canvas.drawCircle(cx, cy, r, it) }
        strokeGlow(dp(1.2f), (alpha * 0.6f).toInt()) { canvas.drawCircle(cx, cy, innerR, it) }

        // Banda exterior girando en sentido horario
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        strokeGlow(dp(2.2f), alpha) { canvas.drawPath(outerTicks, it) }
        canvas.restore()

        // Banda interior girando al revés y más lento
        canvas.save()
        canvas.rotate(-rotation * 0.62f, cx, cy)
        strokeGlow(dp(1.6f), (alpha * 0.8f).toInt()) { canvas.drawPath(innerTicks, it) }
        canvas.restore()

        // Arcos de barrido: segmentos más brillantes que recorren cada banda
        strokePaint.strokeCap = Paint.Cap.ROUND
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        strokeGlow(dp(3.5f), alpha) { canvas.drawArc(oval, rotation * 1.7f, 62f, false, it) }
        strokeGlow(dp(2f), (alpha * 0.55f).toInt()) {
            canvas.drawArc(oval, rotation * 1.7f + 168f, 38f, false, it)
        }
        val innerOval = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        strokeGlow(dp(2.4f), (alpha * 0.8f).toInt()) {
            canvas.drawArc(innerOval, -rotation * 2.2f, 46f, false, it)
        }
        strokePaint.strokeCap = Paint.Cap.BUTT
    }

    /**
     * Paneles unidos al anillo por líneas guía. Entran escalonados: cada uno
     * sale un poco después que el anterior, con su línea dibujándose primero.
     */
    private fun drawPanels(canvas: Canvas, result: DetectionResult, r: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        val margin = dp(12f)
        val padH = dp(10f)
        val padV = dp(7f)
        val gap = r + dp(28f)

        val content = arrayOf(
            resources.getString(R.string.hud_equipment) to EquipmentKnowledge.displayName(result.label),
            resources.getString(R.string.hud_confidence) to "${(result.confidence * 100).toInt()}%",
            resources.getString(R.string.hud_class) to "#${result.classIndex}",
            null to resources.getString(R.string.view_detail)
        )

        // Los paneles se ajustan al contenido, como los del mod, con un tope
        // para que un nombre largo no se coma la pantalla.
        val maxW = min(dp(160f), w * 0.40f)
        val sizes = content.map { (label, value) ->
            val lw = if (label != null) hudLabelPaint.measureText(label) else 0f
            val vw = hudValuePaint.measureText(value)
            val cw = (maxOf(lw, vw) + padH * 2).coerceIn(dp(72f), maxW)
            val ch = padV * 2 + hudValuePaint.fontSpacing +
                    if (label != null) hudLabelPaint.fontSpacing + dp(1f) else 0f
            cw to ch
        }

        val minY = paddingTop + margin
        fun place(i: Int, x: Float, y: Float): RectF {
            val (pw, ph) = sizes[i]
            val maxY = h - bottomReserved - margin - ph
            val l = x.coerceIn(margin, (w - pw - margin).coerceAtLeast(margin))
            val t = y.coerceIn(minY, maxY.coerceAtLeast(minY))
            return RectF(l, t, l + pw, t + ph)
        }

        val rects = arrayOf(
            place(0, cx - sizes[0].first / 2f, cy - gap - sizes[0].second),  // arriba
            place(1, cx + gap, cy - sizes[1].second / 2f),                   // derecha
            place(2, cx - sizes[2].first / 2f, cy + gap),                    // abajo
            place(3, cx - gap - sizes[3].first, cy - sizes[3].second / 2f)   // izquierda
        )
        actionRect = rects[3]

        for (i in content.indices) {
            // Entrada escalonada: 0.30, 0.42, 0.54, 0.66 → 1.0
            val start = 0.30f + i * 0.12f
            val t = ((entrance - start) / (1f - start)).coerceIn(0f, 1f)
            if (t <= 0f) continue

            drawLeader(canvas, rects[i], r, t)

            canvas.save()
            canvas.scale(t, t, rects[i].centerX(), rects[i].centerY())
            val (label, value) = content[i]
            drawPanel(canvas, rects[i], label, value, (255 * t).toInt(), label == null, padV)
            canvas.restore()
        }
    }

    private fun drawLeader(canvas: Canvas, panel: RectF, r: Float, t: Float) {
        val px = panel.centerX(); val py = panel.centerY()
        val dx = px - cx; val dy = py - cy
        val angle = atan2(dy, dx)
        val edgeX = cx + cos(angle) * (r + dp(10f))
        val edgeY = cy + sin(angle) * (r + dp(10f))

        // La línea entra por el borde que mira al anillo. Si se ancla siempre a
        // un lateral, en los paneles de arriba y abajo el trazo los atraviesa.
        val path = Path()
        path.moveTo(edgeX, edgeY)
        if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
            val anchorX = if (dx > 0) panel.left else panel.right
            path.lineTo((edgeX + anchorX) / 2f, py)
            path.lineTo(anchorX, py)
        } else {
            val anchorY = if (dy > 0) panel.top else panel.bottom
            path.lineTo(px, (edgeY + anchorY) / 2f)
            path.lineTo(px, anchorY)
        }
        val measure = PathMeasure(path, false)
        val visible = Path()
        measure.getSegment(0f, measure.length * t, visible, true)

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokeGlow(dp(1.8f), (255 * t).toInt()) { canvas.drawPath(visible, it) }
        strokePaint.style = Paint.Style.FILL
        strokePaint.color = cyan
        strokePaint.alpha = (255 * t).toInt()
        canvas.drawCircle(edgeX, edgeY, dp(3f), strokePaint)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.BUTT
    }

    /** Panel claro translúcido de esquinas rectas, como los del mod. */
    private fun drawPanel(
        canvas: Canvas, rect: RectF, label: String?, value: String,
        alpha: Int, action: Boolean, padV: Float
    ) {
        val fill = if (action) panelFillAction else panelFill
        panelPaint.color = fill
        panelPaint.alpha = Color.alpha(fill) * alpha / 255
        canvas.drawRect(rect, panelPaint)
        strokeGlow(dp(1.6f), alpha) { canvas.drawRect(rect, it) }

        hudLabelPaint.alpha = alpha
        hudValuePaint.alpha = alpha
        var cursor = rect.top + padV
        if (label != null) {
            canvas.drawText(label, rect.centerX(), cursor - hudLabelPaint.ascent(), hudLabelPaint)
            cursor += hudLabelPaint.fontSpacing + dp(1f)
        }
        val clipped = TextUtils.ellipsize(
            value, hudValuePaint, rect.width() - dp(16f), TextUtils.TruncateAt.END
        )
        canvas.drawText(
            clipped, 0, clipped.length, rect.centerX(),
            cursor - hudValuePaint.ascent(), hudValuePaint
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        entranceAnimator.cancel()
        spinAnimator.cancel()
    }

    companion object {
        private const val OUTER_TICKS = 110
        private const val INNER_TICKS = 68
        private const val TRACKING_SMOOTHING = 0.28f
        private const val VIGNETTE_COLOR = 0xFF000000.toInt()
    }
}
