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
            MessageRole.ERROR -> VIEW_TYPE_ERROR
            else -> VIEW_TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (markwon == null) {
            markwon = Markwon.create(parent.context)
        }
        return when (viewType) {
            VIEW_TYPE_USER -> UserViewHolder(
                ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_ERROR -> AssistantViewHolder(
                ItemMessageAssistantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ).apply { isError = true }
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
            markwon?.setMarkdown(binding.textMessage, message.content)
            binding.textTimestamp.text = formatTime(message.timestamp)
        }
    }

    inner class AssistantViewHolder(private val binding: ItemMessageAssistantBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var isError = false
        private var thinkingAnimator: ValueAnimator? = null

        fun bind(message: ChatMessage) {
            val displayContent = message.content

            if (isError) {
                binding.imageAvatar.visibility = android.view.View.GONE
                binding.textAssistantName.visibility = android.view.View.VISIBLE
                binding.textAssistantName.text = "ERROR"
                binding.textAssistantName.setTextColor(
                    android.graphics.Color.parseColor("#CC7A7A")
                )
                binding.textTimestamp.visibility = android.view.View.VISIBLE
                binding.root.setPadding(16, 16, 16, 16)
                binding.textMessage.textSize = 13f
                binding.textMessage.alpha = 0.9f
                (binding.textMessage.parent.parent as? com.google.android.material.card.MaterialCardView)?.apply {
                    setCardBackgroundColor(android.graphics.Color.parseColor("#1ACC7A7A"))
                    strokeColor = android.graphics.Color.parseColor("#66CC7A7A")
                    strokeWidth = 1
                }
            } else if (message.isSystemUpdate) {
                binding.imageAvatar.visibility = android.view.View.GONE
                binding.textAssistantName.visibility = android.view.View.GONE
                binding.textTimestamp.visibility = android.view.View.GONE
                binding.root.setPadding(48, 4, 16, 4)

                binding.textMessage.textSize = 13f
                binding.textMessage.alpha = 0.7f
                (binding.textMessage.parent.parent as? com.google.android.material.card.MaterialCardView)?.apply {
                    setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    strokeWidth = 0
                }
            } else {
                binding.imageAvatar.visibility = android.view.View.VISIBLE
                binding.textAssistantName.visibility = android.view.View.VISIBLE
                binding.textAssistantName.text = "JARVIS"
                binding.textAssistantName.setTextColor(
                    android.graphics.Color.parseColor("#FF8425")
                )
                binding.textTimestamp.visibility = android.view.View.VISIBLE
                binding.root.setPadding(16, 16, 16, 16)
                binding.textMessage.textSize = 15f
                binding.textMessage.alpha = 1f
                (binding.textMessage.parent.parent as? com.google.android.material.card.MaterialCardView)?.apply {
                    setCardBackgroundColor(android.graphics.Color.WHITE)
                    strokeColor = android.graphics.Color.parseColor("#E5E0D8")
                    strokeWidth = 1
                }
            }

            if (!isError && displayContent == THINKING_PLACEHOLDER) {
                startThinkingAnimation(binding.textMessage)
            } else {
                stopThinkingAnimation()
                binding.textMessage.alpha = if (isError) 0.9f else if (message.isSystemUpdate) 0.7f else 1f
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
        private const val VIEW_TYPE_ERROR = 3
    }
}
