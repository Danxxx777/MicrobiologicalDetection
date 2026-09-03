package com.example.microbiologicaldetection.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.microbiologicaldetection.data.ChatMessage
import com.example.microbiologicaldetection.databinding.ActivityChatBinding
import com.example.microbiologicaldetection.network.ApiClient
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    private val equipment by lazy { intent.getStringExtra(EXTRA_EQUIPMENT) ?: "" }
    private val serverUrl by lazy {
        intent.getStringExtra(EXTRA_SERVER_URL) ?: "http://10.0.2.2:5000"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ChatAdapter(messages)
        binding.recyclerChat.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also { it.stackFromEnd = true }
            adapter = this@ChatActivity.adapter
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        if (equipment.isNotBlank()) binding.toolbar.subtitle = equipment

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        addMessage(ChatMessage(text = text, isUser = true))
        binding.etMessage.setText("")

        val typingMsg = ChatMessage(text = "", isUser = false, isTyping = true)
        addMessage(typingMsg)
        val typingIndex = messages.lastIndex

        lifecycleScope.launch {
            val result = ApiClient.sendMessage(serverUrl, equipment, text)
            messages.removeAt(typingIndex)
            result.fold(
                onSuccess = { resp ->
                    addMessage(ChatMessage(text = resp.answer, isUser = false, source = resp.source))
                },
                onFailure = { e ->
                    addMessage(ChatMessage(text = "Error: ${e.message}", isUser = false))
                }
            )
        }
    }

    private fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        adapter.notifyItemInserted(messages.lastIndex)
        binding.recyclerChat.scrollToPosition(messages.lastIndex)
    }

    companion object {
        const val EXTRA_EQUIPMENT = "extra_equipment"
        const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
