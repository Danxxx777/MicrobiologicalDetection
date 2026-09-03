package com.example.microbiologicaldetection.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    var labels: List<String> = emptyList()
        private set

    val isLoaded: Boolean get() = interpreter != null

    // Defaults — overwritten after model loads
    private var inputW = 640
    private var inputH = 640
    private var numClasses = 0
    private var numAnchors = 8400
    private var numCols = 0

    fun load(modelPath: String = "model.tflite", labelsPath: String = "labels.txt"): Boolean {
        return try {
            val model = loadModelFile(modelPath)
            val options = Interpreter.Options()
            options.setNumThreads(4)
            val interp = Interpreter(model, options)
            interpreter = interp

            // Read input shape [1, H, W, 3]
            val inShape = IntArray(4)
            interp.getInputTensor(0).shape().copyInto(inShape)
            inputH = inShape[1]
            inputW = inShape[2]

            // Read output shape [1, cols, anchors]
            val outShape = IntArray(3)
            interp.getOutputTensor(0).shape().copyInto(outShape)
            numCols    = outShape[1]
            numAnchors = outShape[2]

            labels = loadLabels(labelsPath)
            numClasses = labels.size
            true
        } catch (_: Exception) {
            // Shape API failed — fall back to defaults
            try {
                val model = loadModelFile(modelPath)
                val options = Interpreter.Options()
                options.setNumThreads(4)
                interpreter = Interpreter(model, options)
                labels = try { loadLabels(labelsPath) } catch (_: Exception) { emptyList() }
                numClasses = labels.size
                numCols    = 4 + numClasses
                true
            } catch (e2: Exception) {
                e2.printStackTrace()
                false
            }
        }
    }

    fun detect(bitmap: Bitmap, confThreshold: Float = 0.4f, iouThreshold: Float = 0.45f): List<DetectionResult> {
        val interp = interpreter ?: return emptyList()
        if (numClasses == 0) return emptyList()

        val scaled = bitmap.scale(inputW, inputH)
        val inputBuffer = bitmapToBuffer(scaled)

        val cols    = if (numCols > 0) numCols else 4 + numClasses
        val anchors = numAnchors
        val output  = Array(1) { Array(cols) { FloatArray(anchors) }  }

        interp.run(inputBuffer, output)

        val raw = output[0]
        val candidates = mutableListOf<DetectionResult>()

        for (i in 0 until anchors) {
            val cx = raw[0][i]; val cy = raw[1][i]
            val w  = raw[2][i]; val h  = raw[3][i]

            var maxConf = confThreshold
            var bestClass = -1
            for (c in 0 until numClasses) {
                val score = raw[4 + c][i]
                if (score > maxConf) { maxConf = score; bestClass = c }
            }
            if (bestClass < 0) continue

            val x1 = ((cx - w / 2f) / inputW) * bitmap.width
            val y1 = ((cy - h / 2f) / inputH) * bitmap.height
            val x2 = ((cx + w / 2f) / inputW) * bitmap.width
            val y2 = ((cy + h / 2f) / inputH) * bitmap.height

            candidates.add(DetectionResult(
                label      = labels.getOrElse(bestClass) { "clase_$bestClass" },
                confidence = maxConf,
                boundingBox = RectF(x1, y1, x2, y2),
                classIndex = bestClass
            ))
        }

        return nms(candidates, iouThreshold)
    }

    private fun nms(list: List<DetectionResult>, iouThreshold: Float): List<DetectionResult> {
        val sorted = list.sortedByDescending { it.confidence }.toMutableList()
        val kept   = mutableListOf<DetectionResult>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(it.boundingBox, best.boundingBox) > iouThreshold }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val iw = minOf(a.right, b.right)  - maxOf(a.left, b.left)
        val ih = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (iw <= 0 || ih <= 0) return 0f
        val inter = iw * ih
        return inter / (a.width() * a.height() + b.width() * b.height() - inter)
    }

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(inputH * inputW * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputW * inputH)
        bitmap.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        for (px in pixels) {
            buf.putFloat(((px shr 16) and 0xFF) / 255f)
            buf.putFloat(((px shr 8)  and 0xFF) / 255f)
            buf.putFloat(( px         and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        val fd = context.assets.openFd(path)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun loadLabels(path: String): List<String> =
        context.assets.open(path).bufferedReader()
            .readLines().map { it.trim() }.filter { it.isNotEmpty() }

    fun close() { interpreter?.close(); interpreter = null }
}
