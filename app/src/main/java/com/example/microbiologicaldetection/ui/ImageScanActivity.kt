package com.example.microbiologicaldetection.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.lifecycle.lifecycleScope
import com.example.microbiologicaldetection.R
import com.example.microbiologicaldetection.data.EquipmentHistory
import com.example.microbiologicaldetection.data.EquipmentKnowledge
import com.example.microbiologicaldetection.data.EquipmentSession
import com.example.microbiologicaldetection.databinding.ActivityImageScanBinding
import com.example.microbiologicaldetection.ml.DetectionResult
import com.example.microbiologicaldetection.ml.TFLiteDetector
import com.example.microbiologicaldetection.network.RoboflowApiClient
import com.example.microbiologicaldetection.network.RoboflowConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageScanBinding
    private val detector = TFLiteDetector(this)
    private val roboflowClient = RoboflowApiClient()
    private var captureUri: Uri? = null

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::displayAndAnalyze)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) captureUri?.let(::displayAndAnalyze)
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityImageScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.header.applyInsetsPadding(top = true)
        binding.bottomControls.applyInsetsPadding(bottom = true)
        detector.load()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGallery.setOnClickListener {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnCamera.setOnClickListener { requestCamera() }
        binding.overlay.onDetailTapped = ::openDetail
        binding.overlay.onChatTapped = { result ->
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_EQUIPMENT, result.label)
            })
        }
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val directory = File(cacheDir, "captured_equipment").apply { mkdirs() }
        val file = File(directory, "equipment_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        captureUri = uri
        takePicture.launch(uri)
    }

    private fun displayAndAnalyze(uri: Uri) {
        lifecycleScope.launch {
            setLoading(true)
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
            if (bitmap == null) {
                setLoading(false)
                binding.tvAnalysisStatus.setText(R.string.photo_read_error)
                return@launch
            }

            binding.imgSelected.setImageBitmap(bitmap)
            binding.emptyState.visibility = View.GONE
            binding.overlay.results = emptyList()
            binding.imageArea.post {
                binding.overlay.setImageDimensions(bitmap.width, bitmap.height)
                analyze(bitmap)
            }
        }
    }

    private fun analyze(bitmap: Bitmap) {
        lifecycleScope.launch {
            val detections = withContext(Dispatchers.IO) {
                val localResults by lazy {
                    if (detector.isLoaded) detector.detect(bitmap) else emptyList()
                }
                if (RoboflowConfig.isConfigured) {
                    roboflowClient.detect(bitmap).getOrElse { localResults }
                } else {
                    localResults
                }
            }

            setLoading(false)
            val result = detections.firstOrNull()
            if (result == null) {
                binding.overlay.results = emptyList()
                binding.tvAnalysisStatus.setText(R.string.photo_no_detection)
                return@launch
            }

            EquipmentSession.save(this@ImageScanActivity, result.label)
            EquipmentHistory.record(this@ImageScanActivity, result.label, result.confidence)
            binding.tvAnalysisStatus.text = getString(
                R.string.photo_detection_result,
                EquipmentKnowledge.displayName(result.label),
                (result.confidence * 100).toInt()
            )
            binding.overlay.results = listOf(result)
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressAnalysis.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGallery.isEnabled = !loading
        binding.btnCamera.isEnabled = !loading
        if (loading) binding.tvAnalysisStatus.setText(R.string.analyzing_photo)
    }

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) {
                    decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = maxOf(width, height)
                if (largest > MAX_IMAGE_EDGE) {
                    val scale = MAX_IMAGE_EDGE.toFloat() / largest
                    decoder.setTargetSize((width * scale).toInt(), (height * scale).toInt())
                }
            }
        } else {
            val decoded = contentResolver.openInputStream(uri).use(BitmapFactory::decodeStream)
                ?: error("Imagen vacía")
            val largest = maxOf(decoded.width, decoded.height)
            if (largest <= MAX_IMAGE_EDGE) decoded else {
                val scale = MAX_IMAGE_EDGE.toFloat() / largest
                decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt())
            }
        }
    }.getOrNull()

    private fun openDetail(result: DetectionResult) {
        startActivity(Intent(this, EquipmentDetailActivity::class.java).apply {
            putExtra(EquipmentDetailActivity.EXTRA_LABEL, result.label)
            putExtra(EquipmentDetailActivity.EXTRA_CONFIDENCE, result.confidence)
        })
    }

    override fun onDestroy() {
        detector.close()
        super.onDestroy()
    }

    companion object {
        private const val MAX_IMAGE_EDGE = 1600
    }
}
