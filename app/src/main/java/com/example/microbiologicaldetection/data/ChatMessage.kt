package com.example.microbiologicaldetection.data

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val source: String? = null,
    val isTyping: Boolean = false
)
