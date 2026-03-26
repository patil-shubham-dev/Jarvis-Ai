package com.jarvis.assistant.ui.vault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.core.crypto.VaultCrypto
import com.jarvis.assistant.data.models.VaultEntity
import com.jarvis.assistant.databinding.ActivityVaultBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private val db by lazy { (application as JarvisApp).db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authenticate()
    }

    private fun authenticate() {
        val canAuth = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            setupVault()
            return
        }

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = setupVault()
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    Toast.makeText(this@VaultActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                    finish()
                }
                override fun onAuthenticationFailed() {
                    Toast.makeText(this@VaultActivity, "Try again", Toast.LENGTH_SHORT).show()
                }
            })

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Jarvis Vault")
                .setSubtitle("Authenticate to access your secure vault")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }

    private fun setupVault() {
        val adapter = VaultAdapter(
            onView = { entity ->
                try {
                    val plain = VaultCrypto.decrypt(entity.encryptedValue, entity.iv)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(entity.title)
                        .setMessage(plain)
                        .setPositiveButton("OK", null)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Decryption failed", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { entity ->
                AlertDialog.Builder(this)
                    .setTitle("Delete \"${entity.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch { db.vaultDao().delete(entity) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvVault.layoutManager = LinearLayoutManager(this)
        binding.rvVault.adapter = adapter

        lifecycleScope.launch {
            db.vaultDao().observeAll().collectLatest { adapter.submitList(it) }
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener { showAddDialog() }
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_vault, null)
        val etTitle    = view.findViewById<EditText>(R.id.etVaultTitle)
        val etValue    = view.findViewById<EditText>(R.id.etVaultValue)
        val spinner    = view.findViewById<Spinner>(R.id.spinnerVaultCategory)
        val categories = listOf("PASSWORD", "API_KEY", "NOTE", "INFO")

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add to Vault")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim()
                val value = etValue.text.toString().trim()
                if (title.isBlank() || value.isBlank()) {
                    Toast.makeText(this, "Title and value are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val (enc, iv) = VaultCrypto.encrypt(value)
                    db.vaultDao().insert(VaultEntity(
                        category = categories[spinner.selectedItemPosition],
                        title = title,
                        encryptedValue = enc,
                        iv = iv
                    ))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private class VaultAdapter(
    private val onView: (VaultEntity) -> Unit,
    private val onDelete: (VaultEntity) -> Unit
) : ListAdapter<VaultEntity, VaultAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VaultEntity>() {
            override fun areItemsTheSame(a: VaultEntity, b: VaultEntity) = a.id == b.id
            override fun areContentsTheSame(a: VaultEntity, b: VaultEntity) = a == b
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategory: TextView  = view.findViewById(R.id.tvCategory)
        val tvTitle: TextView     = view.findViewById(R.id.tvTitle)
        val btnView: ImageButton  = view.findViewById(R.id.btnView)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_vault, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvCategory.text = item.category
        holder.tvTitle.text    = item.title
        holder.btnView.setOnClickListener   { onView(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }
}
