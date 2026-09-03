package com.example.microbiologicaldetection.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.example.microbiologicaldetection.R
import com.example.microbiologicaldetection.databinding.ActivityMainBinding
import com.example.microbiologicaldetection.ml.DetectionResult
import com.example.microbiologicaldetection.ml.DetectionTracker
import com.example.microbiologicaldetection.ml.TFLiteDetector
import com.example.microbiologicaldetection.data.EquipmentSession
import com.example.microbiologicaldetection.data.EquipmentHistory
import com.example.microbiologicaldetection.network.RoboflowApiClient
import com.example.microbiologicaldetection.network.RoboflowConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val detector = TFLiteDetector(this)
    private val roboflowClient = RoboflowApiClient()

    // Da continuidad a las detecciones: sin esto, una sola inferencia vacia
    // borra el HUD en cuanto la mano se mueve.
    private val tracker = DetectionTracker()

    // Al enfocar un equipo se detiene la inferencia y se muestra el ultimo
    // fotograma: la imagen queda quieta mientras se leen los datos.
    private val frozen = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastFrame: Bitmap? = null

    private var lastInferenceAt = 0L

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var torchOn = false

    // Última ficha abierta, para el acceso directo de la barra inferior
    private var lastResult: DetectionResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // La cámara ocupa toda la pantalla; solo los controles se apartan de las
        // barras del sistema.
        binding.topBar.applyInsetsPadding(top = true)
        // La pildora de estado cuelga bajo la fila de botones, asi que su
        // margen tambien tiene que bajar con la barra de estado.
        binding.bottomBar.applyInsetsPadding(bottom = true)
        binding.fabChat.applyInsetsMargin(bottom = true)
        binding.tvHint.applyInsetsMargin(bottom = true)

        // El overlay dibuja su tarjeta de resultado justo encima de la barra.
        binding.bottomBar.doOnLayout {
            binding.overlay.bottomReserved = it.height.toFloat()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasCameraPermission()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE)

        if (!detector.load()) binding.tvStatus.text = getString(R.string.no_model)

        setUpOverlay()
        setUpControls()
    }

    private fun setUpOverlay() = with(binding.overlay) {
        onScannerOpened = {
            binding.tvHint.visibility = View.GONE
            freezePreview()
        }
        onScannerDismissed = {
            binding.tvHint.visibility = View.VISIBLE
            releasePreview()
        }
        onDetailTapped = { result -> openDetail(result) }
        onChatTapped = { result -> openChat(result.label) }
    }

    private fun setUpControls() {
        binding.fabChat.setOnClickListener { openChat(lastResult?.label) }

        binding.btnRefreshScanner.setOnClickListener {
            tracker.reset()
            binding.overlay.results = emptyList()
            releasePreview()
            lastInferenceAt = 0L
        }

        // Atajo de revisión del HUD (inerte en release, ver debugFocus)
        binding.tvHint.setOnLongClickListener {
            binding.overlay.debugFocus("Autoclave vertical", 0.93f); true
        }

        binding.navHome.setOnClickListener { finish() }

        binding.navHistory.setOnClickListener {
            startActivity(Intent(this, EquipmentHistoryActivity::class.java))
        }

        binding.btnFlash.setOnClickListener {
            val control = camera?.cameraControl ?: return@setOnClickListener
            torchOn = !torchOn
            control.enableTorch(torchOn)
            binding.btnFlash.setImageResource(
                if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
            binding.btnFlash.imageTintList = ContextCompat.getColorStateList(
                this, if (torchOn) R.color.accent else R.color.text_primary
            )
        }

        binding.btnSwitchCamera.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                else CameraSelector.LENS_FACING_BACK
            torchOn = false
            binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
            bindUseCases()
        }
    }

    private fun openDetail(result: DetectionResult) {
        lastResult = result
        binding.navHistory.imageTintList =
            ContextCompat.getColorStateList(this, R.color.text_primary)
        startActivity(Intent(this, EquipmentDetailActivity::class.java).apply {
            putExtra(EquipmentDetailActivity.EXTRA_LABEL, result.label)
            putExtra(EquipmentDetailActivity.EXTRA_CONFIDENCE, result.confidence)
        })
    }

    private fun openChat(equipment: String?) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            if (equipment != null) putExtra(ChatActivity.EXTRA_EQUIPMENT, equipment)
        })
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (frozen.get()) { imageProxy.close(); return@setAnalyzer }
            val now = SystemClock.elapsedRealtime()
            val interval = if (RoboflowConfig.isConfigured) REMOTE_INFERENCE_INTERVAL_MS
            else LOCAL_INFERENCE_INTERVAL_MS
            if (now - lastInferenceAt < interval) {
                imageProxy.close()
                return@setAnalyzer
            }
            lastInferenceAt = now
            processFrame(imageProxy)
        }

        try {
            provider.unbindAll()
            tracker.reset()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            camera = provider.bindToLifecycle(this, selector, preview, analysis)
            binding.btnFlash.visibility =
                if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val t0 = System.currentTimeMillis()
        // ImageAnalysis entrega el frame sin rotar (normalmente apaisado). Hay que
        // enderezarlo para que coincida con lo que muestra PreviewView y con la
        // orientación en la que se entrenó el modelo.
        val rotation = imageProxy.imageInfo.rotationDegrees
        val bitmap = imageProxy.toBitmap().rotated(rotation)
        imageProxy.close()

        val localResults by lazy {
            if (detector.isLoaded) detector.detect(bitmap) else emptyList()
        }
        val results = if (RoboflowConfig.isConfigured) {
            roboflowClient.detect(bitmap).getOrElse { error ->
                Log.w(TAG, "Roboflow inference failed; using local detector", error)
                localResults
            }
        } else {
            localResults
        }
        val ms = System.currentTimeMillis() - t0

        val stable = tracker.update(results)

        runOnUiThread {
            if (frozen.get()) return@runOnUiThread
            lastFrame = bitmap
            binding.overlay.setImageDimensions(bitmap.width, bitmap.height)
            binding.overlay.results = stable
            stable.firstOrNull()?.let { result ->
                if (lastResult?.label != result.label) {
                    EquipmentHistory.record(this, result.label, result.confidence)
                }
                lastResult = result
                EquipmentSession.save(this, result.label)
                binding.navHistory.imageTintList =
                    ContextCompat.getColorStateList(this, R.color.text_primary)
            }
            binding.tvStatus.text = getString(R.string.status_format, stable.size, ms)
        }
    }

    /** Deja la imagen quieta para poder leer y tocar el HUD con calma. */
    private fun freezePreview() {
        frozen.set(true)
        val frame = lastFrame ?: return
        binding.imgFreeze.setImageBitmap(frame)
        binding.imgFreeze.visibility = View.VISIBLE
    }

    private fun releasePreview() {
        binding.imgFreeze.visibility = View.GONE
        binding.imgFreeze.setImageDrawable(null)
        frozen.set(false)
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap {
        if (degrees % 360 == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector.close()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE = 10
        private const val LOCAL_INFERENCE_INTERVAL_MS = 250L
        private const val REMOTE_INFERENCE_INTERVAL_MS = 500L
    }
}
