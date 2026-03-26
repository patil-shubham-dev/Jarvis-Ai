package com.jarvis.assistant.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R

class ChatAdapter(
    private val onSpeak: (String) -> Unit
) : ListAdapter<UiMessage, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_USER   = 0
        private const val TYPE_JARVIS = 1

        private val DIFF = object : DiffUtil.ItemCallback<UiMessage>() {
            override fun areItemsTheSame(a: UiMessage, b: UiMessage) = a.id == b.id
            override fun areContentsTheSame(a: UiMessage, b: UiMessage) = a == b
        }
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == "user") TYPE_USER else TYPE_JARVIS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_msg_user, parent, false))
        } else {
            JarvisViewHolder(inflater.inflate(R.layout.item_msg_jarvis, parent, false), onSpeak)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder   -> holder.bind(getItem(position))
            is JarvisViewHolder -> holder.bind(getItem(position))
        }
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tvText)
        fun bind(msg: UiMessage) { tvText.text = msg.text }
    }

    class JarvisViewHolder(
        view: View,
        private val onSpeak: (String) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView      = view.findViewById(R.id.tvText)
        private val tvBadge: TextView     = view.findViewById(R.id.tvActionBadge)
        private val btnSpeak: ImageButton = view.findViewById(R.id.btnSpeak)

        fun bind(msg: UiMessage) {
            tvText.text = msg.text
            tvBadge.visibility = if (msg.isCommand) View.VISIBLE else View.GONE
            btnSpeak.setOnClickListener { onSpeak(msg.text) }
        }
    }
}
