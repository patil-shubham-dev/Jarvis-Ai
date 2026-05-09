package com.jarvisai.app.ui.activities

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvisai.app.data.models.ChatMessage
import com.jarvisai.app.data.models.MessageRole
import com.jarvisai.app.databinding.ItemMessageUserBinding
import com.jarvisai.app.databinding.ItemMessageAssistantBinding
import io.noties.markwon.Markwon
import android.animation.ValueAnimator

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
        private var thinkingAnimator: ValueAnimator? = null

        fun bind(message: ChatMessage) {
            if (markwon == null) {
                markwon = Markwon.create(binding.root.context)
            }
            
            // Professional UX: Hide raw tool-call JSON from the user
            val displayContent = if (message.content.startsWith("{") && message.content.contains("tool_calls")) {
                "I couldn't complete that device action."
            } else {
                message.content
            }

            // Styling for system updates (autonomous logs)
            if (message.isSystemUpdate) {
                binding.imageAvatar.visibility = android.view.View.GONE
                binding.textAssistantName.visibility = android.view.View.GONE
                binding.textTimestamp.visibility = android.view.View.GONE
                binding.root.setPadding(48, 4, 16, 4) // Indent log entries
                
                // Use a more subtle style for logs
                binding.textMessage.textSize = 13f
                binding.textMessage.alpha = 0.7f
                (binding.textMessage.parent.parent as? com.google.android.material.card.MaterialCardView)?.apply {
                    setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    strokeWidth = 0
                }
            } else {
                binding.imageAvatar.visibility = android.view.View.VISIBLE
                binding.textAssistantName.visibility = android.view.View.VISIBLE
                binding.textTimestamp.visibility = android.view.View.VISIBLE
                binding.root.setPadding(16, 16, 16, 16)
                binding.textMessage.textSize = 15f
                binding.textMessage.alpha = 1f
                (binding.textMessage.parent.parent as? com.google.android.material.card.MaterialCardView)?.apply {
                    setCardBackgroundColor(android.graphics.Color.WHITE)
                    strokeColor = android.graphics.Color.parseColor("#E5E5E5")
                    strokeWidth = 1
                }
            }

            if (displayContent == THINKING_PLACEHOLDER) {
                startThinkingAnimation(binding.textMessage)
            } else {
                stopThinkingAnimation()
                binding.textMessage.alpha = if (message.isSystemUpdate) 0.7f else 1f
                markwon?.setMarkdown(binding.textMessage, displayContent)
            }
            binding.textTimestamp.text = formatTime(message.timestamp)
        }

        private fun startThinkingAnimation(textView: TextView) {
            stopThinkingAnimation()

            val frames = listOf(
                "Jarvis is thinking.",
                "Jarvis is thinking..",
                "Jarvis is thinking..."
            )

            thinkingAnimator = ValueAnimator.ofInt(0, frames.lastIndex).apply {
                duration = 1200L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val index = animator.animatedValue as Int
                    textView.text = frames[index]
                    textView.alpha = 0.75f + (0.25f * (index + 1) / frames.size)
                }
                start()
            }
        }

        private fun stopThinkingAnimation() {
            thinkingAnimator?.cancel()
            thinkingAnimator = null
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
        private const val THINKING_PLACEHOLDER = "Jarvis is thinking"
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }
}
