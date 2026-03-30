package com.jarvisai.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jarvisai.app.R
import com.jarvisai.app.databinding.BottomSheetOnboardingBinding
import com.jarvisai.app.ui.settings.SettingsActivity
import com.jarvisai.app.util.SecurePrefs

class OnboardingBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetOnboardingBinding? = null
    private val binding get() = _binding!!

    private var currentStep = 0

    override fun getTheme(): Int = R.style.Theme_JarvisAI_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        updateUiForStep()

        binding.btnStepAction.setOnClickListener {
            handleAction()
        }

        binding.btnStepSkip.setOnClickListener {
            nextStep()
        }
    }

    override fun onResume() {
        super.onResume()
        // If we came back from settings, auto-advance if permission was granted
        if (currentStep == 0 && Settings.canDrawOverlays(requireContext())) {
            nextStep()
        } else if (currentStep == 1 && isAccessibilityEnabled()) {
            nextStep()
        }
    }

    private fun handleAction() {
        when (currentStep) {
            0 -> {
                // Overlay
                if (!Settings.canDrawOverlays(requireContext())) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${requireContext().packageName}")
                        )
                    )
                } else {
                    nextStep()
                }
            }
            1 -> {
                // Accessibility
                if (!isAccessibilityEnabled()) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    nextStep()
                }
            }
            2 -> {
                // API Key / Finish
                SecurePrefs.setFirstLaunchDone(requireContext())
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                dismiss()
            }
        }
    }

    private fun nextStep() {
        if (currentStep < 2) {
            currentStep++
            updateUiForStep()
        } else {
            SecurePrefs.setFirstLaunchDone(requireContext())
            dismiss()
        }
    }

    private fun updateUiForStep() {
        when (currentStep) {
            0 -> {
                binding.imgStepIcon.setImageResource(R.drawable.ic_overlay)
                binding.textStepTitle.text = "Floating Assistant"
                binding.textStepDesc.text = "Jarvis needs overlay permission to appear smoothly over other apps when you need help."
                binding.btnStepAction.text = "Grant Overlay Permission"
            }
            1 -> {
                binding.imgStepIcon.setImageResource(R.drawable.ic_accessibility)
                binding.textStepTitle.text = "System Actions"
                binding.textStepDesc.text = "To allow Jarvis to read screen context or send messages for you, enable Accessibility Service."
                binding.btnStepAction.text = "Enable Accessibility"
            }
            2 -> {
                binding.imgStepIcon.setImageResource(R.drawable.ic_settings)
                binding.textStepTitle.text = "Add Your Brain"
                binding.textStepDesc.text = "Jarvis is a smart interface. We need an API key (OpenAI, Anthropic, or Gemini) to give it intelligence."
                binding.btnStepAction.text = "Configure API Key"
                binding.btnStepSkip.text = "I'll do it later"
            }
        }
        updateIndicators()
    }

    private fun updateIndicators() {
        binding.rowStepIndicator.removeAllViews()
        for (i in 0..2) {
            val indicator = View(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    if (i == currentStep) dpToPx(24) else dpToPx(8),
                    dpToPx(8)
                ).apply {
                    marginEnd = dpToPx(6)
                }
                background = requireContext().getDrawable(
                    if (i == currentStep) R.drawable.bg_badge_success else R.drawable.bg_badge
                )
            }
            binding.rowStepIndicator.addView(indicator)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${requireContext().packageName}/com.jarvisai.app.service.JarvisAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OnboardingBottomSheet"
    }
}
