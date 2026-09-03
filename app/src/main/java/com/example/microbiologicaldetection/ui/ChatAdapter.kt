package com.example.microbiologicaldetection.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.microbiologicaldetection.R
import com.example.microbiologicaldetection.data.ChatMessage

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageVH>() {

    inner class MessageVH(view: View) : RecyclerView.ViewHolder(view) {
        val layoutUser: View = view.findViewById(R.id.layoutUser)
        val layoutBot: View = view.findViewById(R.id.layoutBot)
        val layoutTyping: View = view.findViewById(R.id.layoutTyping)
        val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
        val tvBotMessage: TextView = view.findViewById(R.id.tvBotMessage)
        val tvSource: TextView = view.findViewById(R.id.tvSource)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageVH(view)
    }

    override fun onBindViewHolder(holder: MessageVH, position: Int) {
        val msg = messages[position]

        holder.layoutUser.visibility = View.GONE
        holder.layoutBot.visibility = View.GONE
        holder.layoutTyping.visibility = View.GONE

        when {
            msg.isTyping -> holder.layoutTyping.visibility = View.VISIBLE
            msg.isUser -> {
                holder.layoutUser.visibility = View.VISIBLE
                holder.tvUserMessage.text = msg.text
            }
            else -> {
                holder.layoutBot.visibility = View.VISIBLE
                holder.tvBotMessage.text = msg.text
                if (!msg.source.isNullOrBlank()) {
                    holder.tvSource.visibility = View.VISIBLE
                    holder.tvSource.text = "Fuente: ${msg.source}"
                } else {
                    holder.tvSource.visibility = View.GONE
                }
            }
        }
    }

    override fun getItemCount() = messages.size
}
