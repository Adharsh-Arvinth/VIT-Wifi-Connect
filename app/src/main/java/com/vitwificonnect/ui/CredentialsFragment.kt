package com.vitwificonnect.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vitwificonnect.R
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.util.HapticHelper

class CredentialsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_credentials, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        val btnBack: ImageButton = view.findViewById(R.id.btn_credentials_back)
        val regLayout: TextInputLayout = view.findViewById(R.id.cred_reg_layout)
        val passLayout: TextInputLayout = view.findViewById(R.id.cred_pass_layout)
        val regInput: TextInputEditText = view.findViewById(R.id.cred_reg_input)
        val passInput: TextInputEditText = view.findViewById(R.id.cred_pass_input)
        val errorText: TextView = view.findViewById(R.id.cred_error_text)
        val btnSave: MaterialButton = view.findViewById(R.id.btn_save_credentials)

        try {
            val accentColor = com.vitwificonnect.data.SettingsStore.getAccentColorHex(ctx)
            btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
            val r = android.graphics.Color.red(accentColor)
            val g = android.graphics.Color.green(accentColor)
            val b = android.graphics.Color.blue(accentColor)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            val textColor = if (luminance > 0.58) android.graphics.Color.parseColor("#08090C") else android.graphics.Color.parseColor("#FFFFFF")
            btnSave.setTextColor(textColor)
        } catch (_: Exception) {}

        // Pre-fill existing registration number and password
        val existingReg = CredentialStore.getRegNumber(ctx)
        if (!existingReg.isNullOrBlank()) {
            regInput.setText(existingReg)
        }
        val existingPass = CredentialStore.getPassword(ctx)
        if (!existingPass.isNullOrBlank()) {
            passInput.setText(existingPass)
        }

        btnBack.setOnClickListener {
            HapticHelper.click(btnBack)
            requireActivity().onBackPressedDispatcher.onBackPressed()
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

        btnSave.setOnClickListener {
            HapticHelper.click(btnSave)

            val reg = regInput.text?.toString()?.trim() ?: ""
            val pass = passInput.text?.toString()?.trim() ?: ""

            if (reg.isBlank()) {
                regLayout.error = getString(R.string.error_reg_required)
                regInput.requestFocus()
                HapticHelper.error(ctx)
                return@setOnClickListener
            }

            if (pass.isBlank()) {
                passLayout.error = getString(R.string.error_pass_required)
                passInput.requestFocus()
                HapticHelper.error(ctx)
                return@setOnClickListener
            }

            val saved = CredentialStore.saveCredentials(ctx, reg, pass)
            if (saved) {
                HapticHelper.success(ctx)
                Toast.makeText(ctx, getString(R.string.msg_credentials_saved), Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            } else {
                HapticHelper.error(ctx)
                errorText.text = getString(R.string.status_error)
                errorText.visibility = View.VISIBLE
            }
        }
    }
}
