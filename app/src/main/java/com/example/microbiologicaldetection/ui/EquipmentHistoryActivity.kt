package com.example.microbiologicaldetection.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.microbiologicaldetection.R
import com.example.microbiologicaldetection.data.EquipmentHistory
import com.example.microbiologicaldetection.data.EquipmentKnowledge
import com.example.microbiologicaldetection.databinding.ActivityEquipmentHistoryBinding
import com.example.microbiologicaldetection.databinding.ItemEquipmentHistoryBinding

class EquipmentHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEquipmentHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEquipmentHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollHistory.applyInsetsPadding(top = true, bottom = true)
        binding.btnBack.setOnClickListener { finish() }
        bindHistory()
    }

    private fun bindHistory() {
        val history = EquipmentHistory.getAll(this)
        binding.tvEmptyHistory.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE

        history.forEach { entry ->
            val item = ItemEquipmentHistoryBinding.inflate(layoutInflater, binding.historyList, false)
            item.tvHistoryName.text = EquipmentKnowledge.displayName(entry.label)
            item.tvHistoryConfidence.text = getString(
                R.string.confidence_value,
                (entry.confidence * 100).toInt()
            )
            item.root.setOnClickListener {
                startActivity(Intent(this, EquipmentDetailActivity::class.java).apply {
                    putExtra(EquipmentDetailActivity.EXTRA_LABEL, entry.label)
                    putExtra(EquipmentDetailActivity.EXTRA_CONFIDENCE, entry.confidence)
                })
            }
            binding.historyList.addView(item.root)
        }
    }
}
