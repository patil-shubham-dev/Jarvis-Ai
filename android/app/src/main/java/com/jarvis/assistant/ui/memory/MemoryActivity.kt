package com.jarvis.assistant.ui.memory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.models.MemoryEntity
import com.jarvis.assistant.databinding.ActivityMemoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MemoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryBinding
    private val db by lazy { (application as JarvisApp).db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = MemoryAdapter { memory ->
            lifecycleScope.launch { db.memoryDao().deleteById(memory.id) }
        }

        binding.rvMemory.layoutManager = LinearLayoutManager(this)
        binding.rvMemory.adapter = adapter

        lifecycleScope.launch {
            db.memoryDao().observeAll().collectLatest { adapter.submitList(it) }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all memories?")
                .setMessage("Jarvis will forget everything it has learned about you. This cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch { db.memoryDao().deleteAll() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

private class MemoryAdapter(
    private val onDelete: (MemoryEntity) -> Unit
) : ListAdapter<MemoryEntity, MemoryAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MemoryEntity>() {
            override fun areItemsTheSame(a: MemoryEntity, b: MemoryEntity) = a.id == b.id
            override fun areContentsTheSame(a: MemoryEntity, b: MemoryEntity) = a == b
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvModule: TextView    = view.findViewById(R.id.tvModule)
        val tvKey: TextView       = view.findViewById(R.id.tvKey)
        val tvValue: TextView     = view.findViewById(R.id.tvValue)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvModule.text = item.module
        holder.tvKey.text    = item.key
        holder.tvValue.text  = item.value
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }
}
