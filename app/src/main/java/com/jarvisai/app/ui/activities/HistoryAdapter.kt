package com.jarvisai.app.ui.activities

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvisai.app.data.models.ChatSession
import com.jarvisai.app.databinding.ItemHistorySessionBinding

class HistoryAdapter(
    private val onSessionClick: (ChatSession) -> Unit
) : ListAdapter<ChatSession, HistoryAdapter.HistoryViewHolder>(ChatSessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        return HistoryViewHolder(
            ItemHistorySessionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onSessionClick
        )
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistorySessionBinding,
        private val onSessionClick: (ChatSession) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(session: ChatSession) {
            binding.txtSessionTitle.text = session.title
            binding.root.setOnClickListener {
                onSessionClick(session)
            }
        }
    }

    class ChatSessionDiffCallback : DiffUtil.ItemCallback<ChatSession>() {
        override fun areItemsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem == newItem
        }
    }
}
