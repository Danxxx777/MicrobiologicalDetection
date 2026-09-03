package com.example.microbiologicaldetection.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.microbiologicaldetection.R
import com.example.microbiologicaldetection.data.ChatMessage
import com.example.microbiologicaldetection.data.EquipmentSession
import com.example.microbiologicaldetection.data.EquipmentKnowledge
import com.example.microbiologicaldetection.databinding.ActivityChatBinding
import com.example.microbiologicaldetection.network.ApiClient
import kotlinx.coroutines.launch
import java.util.Locale

class ChatActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityChatBinding
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private var textToSpeech: TextToSpeech? = null
    private var speakNextResponse = false
    /**
     * Reconocedor propio. Se usa [SpeechRecognizer] en lugar de
     * RecognizerIntent porque este ultimo abre la ventana blanca de Google
     * encima de la app; el reconocedor no tiene interfaz y deja que el estado
     * de escucha se muestre dentro del chat.
     */
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else Toast.makeText(this, R.string.voice_permission_required, Toast.LENGTH_LONG).show()
    }

    private val equipment by lazy {
        intent.getStringExtra(EXTRA_EQUIPMENT)
            ?.takeIf { it.isNotBlank() }
            ?: EquipmentSession.get(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        textToSpeech = TextToSpeech(this, this)

        binding.header.applyInsetsPadding(top = true)
        binding.inputBar.applyInsetsPadding(bottom = true, ime = true)

        adapter = ChatAdapter(messages)
        binding.recyclerChat.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also { it.stackFromEnd = true }
            adapter = this@ChatActivity.adapter
        }

        binding.btnBack.setOnClickListener { finish() }
        if (equipment.isNotBlank()) {
            binding.tvEquipmentContext.text = EquipmentKnowledge.displayName(equipment)
            binding.tvEquipmentContext.visibility = View.VISIBLE
        }

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnVoice.setOnClickListener { startVoiceInput() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        if (intent.getBooleanExtra(EXTRA_START_VOICE, false)) {
            binding.root.post { startVoiceInput() }
        }
    }

    /** Tocar el microfono alterna entre escuchar y cancelar. */
    private fun startVoiceInput() {
        if (listening) { stopListening(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        textToSpeech?.stop()
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(voiceListener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-EC")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        setListening(true)
        engine.startListening(intent)
    }

    private fun stopListening() {
        recognizer?.stopListening()
        setListening(false)
    }

    /** Estado visible de la escucha, dentro de la propia pantalla. */
    private fun setListening(active: Boolean) {
        listening = active
        binding.btnVoice.setBackgroundResource(
            if (active) R.drawable.bg_circle_listening else R.drawable.bg_circle_action
        )
        binding.btnVoice.imageTintList = ContextCompat.getColorStateList(
            this, if (active) R.color.on_accent else R.color.accent
        )
        binding.etMessage.hint = getString(
            if (active) R.string.voice_listening else R.string.chat_hint
        )
    }

    private val voiceListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = setListening(true)
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = setListening(false)
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank()) binding.etMessage.setText(partial)
        }

        override fun onResults(results: Bundle?) {
            setListening(false)
            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isBlank()) return
            speakNextResponse = true
            binding.etMessage.setText(spoken)
            sendMessage()
        }

        override fun onError(error: Int) {
            setListening(false)
            binding.etMessage.setText("")
            // Cancelar a proposito no es un fallo que valga la pena anunciar.
            if (error == SpeechRecognizer.ERROR_CLIENT) return
            val message = if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) R.string.voice_no_speech else R.string.voice_error
            Toast.makeText(this@ChatActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        val history = messages
            .filterNot { it.isTyping }
            .map { message ->
                ApiClient.ChatTurn(
                    role = if (message.isUser) "user" else "model",
                    text = message.text
                )
            }

        addMessage(ChatMessage(text = text, isUser = true))
        binding.etMessage.setText("")

        val typingMsg = ChatMessage(text = "", isUser = false, isTyping = true)
        addMessage(typingMsg)

        lifecycleScope.launch {
            val result = ApiClient.sendMessage(equipment, text, history)
            removeMessage(typingMsg)
            result.fold(
                onSuccess = { resp ->
                    addMessage(ChatMessage(text = resp.answer, isUser = false, source = resp.source))
                    if (speakNextResponse) {
                        textToSpeech?.speak(resp.answer, TextToSpeech.QUEUE_FLUSH, null, "gemini-response")
                        speakNextResponse = false
                    }
                },
                onFailure = { e ->
                    speakNextResponse = false
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

    private fun removeMessage(msg: ChatMessage) {
        val index = messages.indexOfFirst { it === msg }
        if (index == -1) return
        messages.removeAt(index)
        adapter.notifyItemRemoved(index)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.forLanguageTag("es-EC")
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_EQUIPMENT = "extra_equipment"
        const val EXTRA_START_VOICE = "extra_start_voice"
    }
}
