package com.example.microbiologicaldetection.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.microbiologicaldetection.databinding.ActivityMainBinding
import com.example.microbiologicaldetection.ml.TFLiteDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val detector = TFLiteDetector(this)

    // true = scanner visible, freeze new frames
    private val frozen = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasCameraPermission()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE)

        if (!detector.load()) binding.tvStatus.text = "modelo no encontrado"

        // When scanner opens → freeze camera
        binding.overlay.onScannerOpened = { frozen.set(true) }

        // When scanner closes → unfreeze camera
        binding.overlay.onScannerDismissed = { frozen.set(false) }

        // When user taps "Consultar IA" panel inside the scanner
        binding.overlay.onChatTapped = { result ->
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_EQUIPMENT, result.label)
            })
        }

        // FAB opens chat without a specific equipment
        binding.fabChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_LONG).show()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                // Drop frames while scanner is showing
                if (frozen.get()) { imageProxy.close(); return@setAnalyzer }
                processFrame(imageProxy)
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val t0 = System.currentTimeMillis()
        val bitmap = imageProxy.toBitmap()
        imageProxy.close()

        val results = if (detector.isLoaded) detector.detect(bitmap) else emptyList()
        val ms = System.currentTimeMillis() - t0

        runOnUiThread {
            if (frozen.get()) return@runOnUiThread   // scanner open, don't update UI
            binding.overlay.setImageDimensions(bitmap.width, bitmap.height)
            binding.overlay.results = results
            binding.tvStatus.text = "${results.size} detecciones  |  $ms ms"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector.close()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE = 10
    }
}
