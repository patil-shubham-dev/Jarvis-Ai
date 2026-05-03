package com.jarvisai.app.ui.activities

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jarvisai.app.databinding.ItemMemoryModuleBinding

class MemoryModuleAdapter(
    private val modules: List<MemoryModule>,
    private val onModuleClick: (MemoryModule) -> Unit
) : RecyclerView.Adapter<MemoryModuleAdapter.MemoryModuleViewHolder>() {

    data class MemoryModule(
        val name: String,
        val emoji: String,
        val description: String
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryModuleViewHolder {
        return MemoryModuleViewHolder(
            ItemMemoryModuleBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onModuleClick
        )
    }

    override fun onBindViewHolder(holder: MemoryModuleViewHolder, position: Int) {
        holder.bind(modules[position])
    }

    override fun getItemCount(): Int = modules.size

    inner class MemoryModuleViewHolder(
        private val binding: ItemMemoryModuleBinding,
        private val onModuleClick: (MemoryModule) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(module: MemoryModule) {
            binding.textModuleName.text = "${module.emoji} ${module.name}"
            binding.textModuleDesc.text = module.description
            binding.root.setOnClickListener {
                onModuleClick(module)
            }
        }
    }
}
