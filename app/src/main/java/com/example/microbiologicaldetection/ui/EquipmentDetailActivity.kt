package com.example.microbiologicaldetection.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.microbiologicaldetection.R
import com.example.microbiologicaldetection.data.EquipmentSheet
import com.example.microbiologicaldetection.data.EquipmentKnowledge
import com.example.microbiologicaldetection.data.EquipmentSession
import com.example.microbiologicaldetection.databinding.ActivityEquipmentDetailBinding
import com.example.microbiologicaldetection.databinding.ItemDatasheetSectionBinding
import com.example.microbiologicaldetection.network.ApiClient
import kotlinx.coroutines.launch

class EquipmentDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEquipmentDetailBinding

    private val label by lazy { intent.getStringExtra(EXTRA_LABEL) ?: "Equipo" }
    private val confidence by lazy { intent.getFloatExtra(EXTRA_CONFIDENCE, 0f) }

    private var sheet: EquipmentSheet? = null
    private var loadingSheet = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEquipmentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.header.applyInsetsPadding(top = true)
        binding.bottomBar.applyInsetsPadding(bottom = true)
        binding.fabOpenChat.applyInsetsMargin(bottom = true)

        binding.tvEquipmentName.text = EquipmentKnowledge.displayName(label)
        binding.tvConfidence.text =
            getString(R.string.confidence_value, (confidence * 100).toInt())
        EquipmentSession.save(this, label)

        sheet = EquipmentKnowledge.sheetFor(label)
        loadingSheet = sheet == null

        bindSections()
        bindActions()
        loadEquipmentSheet()
    }

    private fun bindSections() {
        val fallback = getString(if (loadingSheet) R.string.section_loading else R.string.section_empty)
        fun fill(section: ItemDatasheetSectionBinding, titleRes: Int, body: String?) {
            section.tvSectionTitle.setText(titleRes)
            section.tvSectionBody.text = body?.takeIf { it.isNotBlank() } ?: fallback
        }
        fill(binding.sectionComponents, R.string.components_title, sheet?.components)
        fill(binding.sectionProcedure, R.string.procedure_title, sheet?.procedure)
        fill(binding.sectionPpe, R.string.ppe_title, sheet?.ppe)
        fill(binding.sectionRisks, R.string.risks_title, sheet?.risks)
        fill(binding.sectionPractices, R.string.practices_title, sheet?.practices)
    }

    private fun loadEquipmentSheet() {
        lifecycleScope.launch {
            ApiClient.generateEquipmentSheet(label).fold(
                onSuccess = { loaded ->
                    // El instructivo institucional manda; Gemini solo completa huecos.
                    sheet = sheet?.completedWith(loaded) ?: loaded
                    loadingSheet = false
                    bindSections()
                },
                onFailure = { error ->
                    Log.w(TAG, "No se pudo ampliar la ficha con Gemini", error)
                    loadingSheet = false
                    bindSections()
                    if (sheet == null) {
                        Toast.makeText(this@EquipmentDetailActivity, error.message, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun bindActions() {
        binding.btnBack.setOnClickListener { finish() }
        binding.navScan.setOnClickListener { finish() }
        binding.btnScanAgain.setOnClickListener { finish() }

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, buildPlainText()))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, label)
                putExtra(Intent.EXTRA_TEXT, buildPlainText())
            }
            startActivity(Intent.createChooser(send, getString(R.string.share)))
        }

        binding.fabOpenChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_EQUIPMENT, label)
            })
        }
    }

    /** La ficha completa en texto plano, para copiar o compartir. */
    private fun buildPlainText(): String = buildString {
        appendLine(label)
        appendLine(getString(R.string.confidence_value, (confidence * 100).toInt()))
        listOf(
            R.string.components_title to sheet?.components,
            R.string.procedure_title to sheet?.procedure,
            R.string.ppe_title to sheet?.ppe,
            R.string.risks_title to sheet?.risks,
            R.string.practices_title to sheet?.practices,
        ).forEach { (titleRes, body) ->
            if (!body.isNullOrBlank()) {
                appendLine()
                appendLine(getString(titleRes))
                appendLine(body)
            }
        }
    }.trim()

    companion object {
        private const val TAG = "EquipmentDetail"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_CONFIDENCE = "extra_confidence"
    }
}
