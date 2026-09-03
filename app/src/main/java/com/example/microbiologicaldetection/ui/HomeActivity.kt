package com.example.microbiologicaldetection.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.microbiologicaldetection.data.EquipmentKnowledge
import com.example.microbiologicaldetection.data.EquipmentSession
import com.example.microbiologicaldetection.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // El inset va al scroll, no al root: la portada debe seguir
        // sangrando por debajo de la barra de estado.
        binding.scrollHome.applyInsetsPadding(top = true, bottom = true)
        binding.btnStartScan.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bindLastEquipment()
    }

    /**
     * Atajo al ultimo equipo escaneado. Se resuelve en onResume porque al
     * volver de la camara la sesion pudo cambiar.
     */
    private fun bindLastEquipment() {
        val last = EquipmentSession.get(this)
        if (last.isBlank()) {
            binding.cardLastEquipment.visibility = View.GONE
            return
        }
        binding.tvLastEquipment.text = EquipmentKnowledge.displayName(last)
        binding.cardLastEquipment.visibility = View.VISIBLE
        binding.cardLastEquipment.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_EQUIPMENT, last)
            })
        }
    }
}
