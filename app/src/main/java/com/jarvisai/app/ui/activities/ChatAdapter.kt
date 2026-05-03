package com.jarvisai.app.ui.activities

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvisai.app.data.models.ChatMessage
import com.jarvisai.app.data.models.MessageRole
import com.jarvisai.app.databinding.ItemMessageUserBinding
import com.jarvisai.app.databinding.ItemMessageAssistantBinding
import io.noties.markwon.Markwon

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {
    
    private var markwon: Markwon? = null

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
            MessageRole.USER -> VIEW_TYPE_USER
            else -> VIEW_TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> UserViewHolder(
                ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> AssistantViewHolder(
                ItemMessageAssistantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> holder.bind(getItem(position))
            is AssistantViewHolder -> holder.bind(getItem(position))
        }
    }

    inner class UserViewHolder(private val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            if (markwon == null) {
                markwon = Markwon.create(binding.root.context)
            }
            markwon?.setMarkdown(binding.textMessage, message.content)
            binding.textTimestamp.text = formatTime(message.timestamp)
        }
    }

    inner class AssistantViewHolder(private val binding: ItemMessageAssistantBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            if (markwon == null) {
                markwon = Markwon.create(binding.root.context)
            }
            
            // Professional UX: Hide raw tool-call JSON from the user
            val displayContent = if (message.content.startsWith("{") && message.content.contains("tool_calls")) {
                "Processing system actions..."
            } else {
                message.content
            }
            
            markwon?.setMarkdown(binding.textMessage, displayContent)
            binding.textTimestamp.text = formatTime(message.timestamp)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.content == newItem.content && oldItem.timestamp == newItem.timestamp
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }
}
