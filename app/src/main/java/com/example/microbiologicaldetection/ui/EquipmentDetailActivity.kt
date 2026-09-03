package com.example.microbiologicaldetection.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.microbiologicaldetection.databinding.ActivityEquipmentDetailBinding

class EquipmentDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEquipmentDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEquipmentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Equipo"
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f)

        binding.tvEquipmentName.text = label
        binding.tvConfidence.text = "Confianza: ${(confidence * 100).toInt()}%"

        binding.fabOpenChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_EQUIPMENT, label)
            }
            startActivity(intent)
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_CONFIDENCE = "extra_confidence"
    }
}
