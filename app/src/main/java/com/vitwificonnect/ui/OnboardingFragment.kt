package com.vitwificonnect.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vitwificonnect.R
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.data.SettingsStore
import com.vitwificonnect.util.HapticHelper

class OnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val regLayout: TextInputLayout = view.findViewById(R.id.onboard_reg_layout)
        val passLayout: TextInputLayout = view.findViewById(R.id.onboard_pass_layout)
        val regInput: TextInputEditText = view.findViewById(R.id.onboard_reg_input)
        val passInput: TextInputEditText = view.findViewById(R.id.onboard_pass_input)
        val autoSwitch: SwitchCompat = view.findViewById(R.id.onboard_auto_login_switch)
        val errorText: TextView = view.findViewById(R.id.onboard_error_text)
        val submitButton: MaterialButton = view.findViewById(R.id.onboard_submit_button)

        val ctx = requireContext()
        try {
            val accentColor = com.vitwificonnect.data.SettingsStore.getAccentColorHex(ctx)
            submitButton.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
            val r = android.graphics.Color.red(accentColor)
            val g = android.graphics.Color.green(accentColor)
            val b = android.graphics.Color.blue(accentColor)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            val textColor = if (luminance > 0.58) android.graphics.Color.parseColor("#08090C") else android.graphics.Color.parseColor("#FFFFFF")
            submitButton.setTextColor(textColor)

            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
            val thumbColors = intArrayOf(accentColor, android.graphics.Color.parseColor("#71717A"))
            val trackColors = intArrayOf(android.graphics.Color.argb(100, r, g, b), android.graphics.Color.argb(50, 113, 113, 122))
            autoSwitch.thumbTintList = android.content.res.ColorStateList(states, thumbColors)
            autoSwitch.trackTintList = android.content.res.ColorStateList(states, trackColors)
        } catch (_: Exception) {}

        autoSwitch.setOnCheckedChangeListener { buttonView, _ ->
            HapticHelper.toggle(buttonView)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                regLayout.error = null
                passLayout.error = null
                errorText.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        regInput.addTextChangedListener(watcher)
        passInput.addTextChangedListener(watcher)

        submitButton.setOnClickListener {
            HapticHelper.click(submitButton)

            val reg = regInput.text?.toString()?.trim() ?: ""
            val pass = passInput.text?.toString()?.trim() ?: ""

            if (reg.isBlank()) {
                regLayout.error = getString(R.string.error_reg_required)
                regInput.requestFocus()
                HapticHelper.error(requireContext())
                return@setOnClickListener
            }

            if (pass.isBlank()) {
                passLayout.error = getString(R.string.error_pass_required)
                passInput.requestFocus()
                HapticHelper.error(requireContext())
                return@setOnClickListener
            }

            val ctx = requireContext()
            CredentialStore.saveCredentials(ctx, reg, pass)
            SettingsStore.setAutoLogin(ctx, autoSwitch.isChecked)
            SettingsStore.setOnboardingCompleted(ctx, true)

            HapticHelper.success(ctx)

            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
    }
}
