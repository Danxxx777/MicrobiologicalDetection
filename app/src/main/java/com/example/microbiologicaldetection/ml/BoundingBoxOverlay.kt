package com.example.microbiologicaldetection.ml

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Colors ────────────────────────────────────────────────────────────────
    private val boxColors = intArrayOf(
        Color.parseColor("#FF6B35"), Color.parseColor("#00C8FF"),
        Color.parseColor("#4444FF"), Color.parseColor("#22a005"),
        Color.parseColor("#FF44AA"), Color.parseColor("#FFD700"),
        Color.parseColor("#FF3333"), Color.parseColor("#AA44FF"),
        Color.parseColor("#00FFAA"), Color.parseColor("#FF8800"),
        Color.parseColor("#00AAFF"), Color.parseColor("#FFAA00"),
    )
    private val SCANNER_COLOR = Color.parseColor("#00E5FF")
    private val SCANNER_DIM   = Color.parseColor("#4400E5FF")
    private val PANEL_BG      = Color.parseColor("#CC001A2E")
    private val PANEL_ACCENT  = Color.parseColor("#00E5FF")
    private val CHAT_BG       = Color.parseColor("#CC00695C")
    private val CHAT_ACCENT   = Color.parseColor("#1DE9B6")

    // ── Bounding box paints ───────────────────────────────────────────────────
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 36f; isFakeBoldText = true
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    // ── Scanner paints ────────────────────────────────────────────────────────
    private val scanRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = SCANNER_COLOR
    }
    private val scanArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; color = SCANNER_COLOR
        strokeCap = Paint.Cap.ROUND
    }
    private val scanFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = PANEL_BG
    }
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = SCANNER_COLOR
    }
    private val scanTextLargePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 38f; isFakeBoldText = true
    }
    private val scanTextSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SCANNER_COLOR; textSize = 26f
    }
    private val scanTextLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF"); textSize = 22f
    }
    private val chatTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CHAT_ACCENT; textSize = 32f; isFakeBoldText = true
    }
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = SCANNER_COLOR
    }
    private val chatBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = CHAT_ACCENT
    }
    private val chatFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = CHAT_BG
    }
    private val dimPaint = Paint().apply {
        color = Color.parseColor("#88000000"); style = Paint.Style.FILL
    }

    // ── State ─────────────────────────────────────────────────────────────────
    var results: List<DetectionResult> = emptyList()
        set(value) { field = value; if (scannerResult == null) invalidate() }

    var onChatTapped: ((DetectionResult) -> Unit)? = null
    var onScannerOpened: (() -> Unit)? = null
    var onScannerDismissed: (() -> Unit)? = null

    private var imgScaleX = 1f
    private var imgScaleY = 1f

    private var scannerResult: DetectionResult? = null
    private var scannerCx = 0f
    private var scannerCy = 0f
    private var scannerR  = 0f
    private var rotAngle  = 0f
    private var chatPanelRect = RectF()

    private val rotAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { rotAngle = it.animatedValue as Float; invalidate() }
    }

    fun setImageDimensions(imgW: Int, imgH: Int) {
        val vw = width.toFloat(); val vh = height.toFloat()
        if (vw == 0f || vh == 0f || imgW == 0 || imgH == 0) return
        imgScaleX = vw / imgW; imgScaleY = vh / imgH
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val tx = event.x; val ty = event.y

        // If scanner is showing — check chat panel tap or dismiss
        if (scannerResult != null) {
            if (chatPanelRect.contains(tx, ty)) {
                onChatTapped?.invoke(scannerResult!!)
            }
            dismissScanner()
            return true
        }

        // Normal mode — tap a bbox to show scanner
        for (result in results.reversed()) {
            if (scaledBox(result.boundingBox).contains(tx, ty)) {
                showScanner(result); return true
            }
        }
        return true
    }

    private fun showScanner(result: DetectionResult) {
        val box = scaledBox(result.boundingBox)
        scannerCx = box.centerX(); scannerCy = box.centerY()
        scannerR  = (maxOf(box.width(), box.height()) / 2f * 1.1f).coerceIn(80f, 300f)
        scannerResult = result
        onScannerOpened?.invoke()
        rotAnimator.start(); invalidate()
    }

    private fun dismissScanner() {
        scannerResult = null
        rotAnimator.cancel()
        onScannerDismissed?.invoke()
        invalidate()
    }

    private fun scaledBox(box: RectF) = RectF(
        box.left * imgScaleX, box.top * imgScaleY,
        box.right * imgScaleX, box.bottom * imgScaleY
    )

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scannerResult != null) {
            drawScanner(canvas, scannerResult!!)
        } else {
            drawBoundingBoxes(canvas)
        }
    }

    private fun drawBoundingBoxes(canvas: Canvas) {
        for (result in results) {
            val color = boxColors[result.classIndex % boxColors.size]
            val box = scaledBox(result.boundingBox)
            boxPaint.color = color
            canvas.drawRect(box, boxPaint)
            val label = "${result.label}  ${(result.confidence * 100).toInt()}%"
            val textH = labelPaint.fontSpacing
            val textW = labelPaint.measureText(label)
            val top = (box.top - textH - 4f).coerceAtLeast(0f)
            labelBgPaint.color = color
            canvas.drawRect(box.left, top, box.left + textW + 16f, top + textH + 4f, labelBgPaint)
            canvas.drawText(label, box.left + 8f, top + textH - 4f, labelPaint)
        }
    }

    private fun drawScanner(canvas: Canvas, result: DetectionResult) {
        val cx = scannerCx; val cy = scannerCy; val r = scannerR
        val w = width.toFloat(); val h = height.toFloat()

        // Dim background
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        // Inner fill circle
        scanFillPaint.color = PANEL_BG
        canvas.drawCircle(cx, cy, r * 0.85f, scanFillPaint)

        // Outer ring (static tick marks)
        val tickCount = 72
        for (i in 0 until tickCount) {
            val angle = Math.toRadians((i * 360.0 / tickCount))
            val tickLen = if (i % 6 == 0) r * 0.12f else r * 0.06f
            val x1 = cx + cos(angle).toFloat() * r
            val y1 = cy + sin(angle).toFloat() * r
            val x2 = cx + cos(angle).toFloat() * (r + tickLen)
            val y2 = cy + sin(angle).toFloat() * (r + tickLen)
            scanRingPaint.alpha = if (i % 6 == 0) 255 else 120
            canvas.drawLine(x1, y1, x2, y2, scanRingPaint)
        }

        // Static ring circle
        scanRingPaint.alpha = 160
        canvas.drawCircle(cx, cy, r, scanRingPaint)

        // Rotating arcs
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        scanArcPaint.alpha = 255
        canvas.drawArc(oval, rotAngle, 80f, false, scanArcPaint)
        scanArcPaint.alpha = 120
        canvas.drawArc(oval, rotAngle + 180f, 50f, false, scanArcPaint)

        // Center crosshair
        scanLinePaint.alpha = 80
        canvas.drawLine(cx - r * 0.6f, cy, cx + r * 0.6f, cy, scanLinePaint)
        canvas.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.6f, scanLinePaint)
        scanLinePaint.alpha = 255

        // ── Panels layout ────────────────────────────────────────────────────
        val panelW = 280f; val panelH = 80f
        val gap = r + 30f

        // Clamp panels inside screen
        fun clampX(x: Float, pw: Float) = x.coerceIn(8f, w - pw - 8f)
        fun clampY(y: Float, ph: Float) = y.coerceIn(8f, h - ph - 8f)

        // TOP panel — Equipment name
        val namePanel = RectF(
            clampX(cx - panelW / 2f, panelW), clampY(cy - gap - panelH, panelH),
            clampX(cx - panelW / 2f, panelW) + panelW, clampY(cy - gap - panelH, panelH) + panelH
        )
        // RIGHT panel — Confidence
        val confPanel = RectF(
            clampX(cx + gap, panelW), clampY(cy - panelH / 2f, panelH),
            clampX(cx + gap, panelW) + panelW, clampY(cy - panelH / 2f, panelH) + panelH
        )
        // BOTTOM panel — Class index
        val classPanel = RectF(
            clampX(cx - panelW / 2f, panelW), clampY(cy + gap, panelH),
            clampX(cx - panelW / 2f, panelW) + panelW, clampY(cy + gap, panelH) + panelH
        )
        // LEFT panel — Chat button
        val chatW = 260f; val chatH = 80f
        val chatRect = RectF(
            clampX(cx - gap - chatW, chatW), clampY(cy - chatH / 2f, chatH),
            clampX(cx - gap - chatW, chatW) + chatW, clampY(cy - chatH / 2f, chatH) + chatH
        )
        chatPanelRect = chatRect  // save for touch detection

        // Draw connector lines from circle edge to panel
        fun lineToPanel(panel: RectF) {
            val px = panel.centerX(); val py = panel.centerY()
            val angle = atan2(py - cy, px - cx)
            val edgeX = cx + cos(angle) * r; val edgeY = cy + sin(angle) * r
            // corner of panel closest to circle
            val pcx = (edgeX + px) / 2f
            canvas.drawLine(edgeX, edgeY, pcx, py, scanLinePaint)
            canvas.drawLine(pcx, py, px - (px - panel.left).sign * panelW / 2f, py, scanLinePaint)
            // dot at edge
            canvas.drawCircle(edgeX, edgeY, 5f, scanFillPaint.also { it.color = SCANNER_COLOR })
        }
        lineToPanel(namePanel); lineToPanel(confPanel)
        lineToPanel(classPanel); lineToPanel(chatRect)

        // Draw info panels
        drawInfoPanel(canvas, namePanel, "EQUIPO", result.label)
        drawInfoPanel(canvas, confPanel, "CONFIANZA", "${(result.confidence * 100).toInt()}%")
        drawInfoPanel(canvas, classPanel, "CLASE", "#${result.classIndex}")
        drawChatPanel(canvas, chatRect)
    }

    private fun drawInfoPanel(canvas: Canvas, rect: RectF, label: String, value: String) {
        val rr = 10f
        scanFillPaint.color = PANEL_BG
        canvas.drawRoundRect(rect, rr, rr, scanFillPaint)
        canvas.drawRoundRect(rect, rr, rr, panelBorderPaint)
        // Top-left corner accent
        canvas.drawLine(rect.left, rect.top, rect.left + 30f, rect.top, panelBorderPaint.also { it.color = SCANNER_COLOR; it.strokeWidth = 3f })
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + 16f, panelBorderPaint)
        panelBorderPaint.color = SCANNER_COLOR; panelBorderPaint.strokeWidth = 2f

        canvas.drawText(label, rect.left + 12f, rect.top + 26f, scanTextLabelPaint)
        val valText = if (value.length > 16) value.take(14) + "…" else value
        canvas.drawText(valText, rect.left + 12f, rect.top + 62f, scanTextLargePaint)
    }

    private fun drawChatPanel(canvas: Canvas, rect: RectF) {
        val rr = 10f
        chatFillPaint.color = CHAT_BG
        canvas.drawRoundRect(rect, rr, rr, chatFillPaint)
        canvas.drawRoundRect(rect, rr, rr, chatBorderPaint)
        // Corner accent
        canvas.drawLine(rect.right - 30f, rect.top, rect.right, rect.top, chatBorderPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + 16f, chatBorderPaint)

        canvas.drawText("▶  CONSULTAR IA", rect.left + 18f, rect.centerY() + 12f, chatTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotAnimator.cancel()
    }
}
