package com.example.pesatrackexpensetrackapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.databinding.FragmentLoginBinding
import com.example.pesatrackexpensetrackapp.utils.BiometricAuth
import com.example.pesatrackexpensetrackapp.utils.BiometricCredentialStore
import com.example.pesatrackexpensetrackapp.utils.BiometricSupport

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.authViewModelFactory
    }
    private val credentialStore by lazy { BiometricCredentialStore(requireContext()) }
    private var fingerprintAttempt = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.clearFeedback()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
        binding.forgotPasswordButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgot_password)
        }
        binding.signInActionButton.setOnClickListener { submit() }
        binding.passwordInput.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        refreshFingerprintButton()
        binding.biometricButton.setOnClickListener { signInWithFingerprint() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.emailInput.error = state.emailError?.let(::getString)
            binding.passwordInput.error = state.passwordError?.let(::getString)
            binding.messageText.isVisible = state.message != null
            state.message?.let(binding.messageText::setText)
            binding.messageText.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (state.messageIsSuccess) R.color.forest_700 else R.color.coral,
                ),
            )
            binding.loadingIndicator.isVisible = state.isLoading
            binding.signInActionButton.isEnabled = !state.isLoading
            // The spinner replaces the label while the request is running.
            binding.signInActionButton.text = if (state.isLoading) "" else getString(R.string.action_sign_in)
            binding.biometricButton.isEnabled = !state.isLoading

            // Saved fingerprint credentials that the server rejects (password changed) are removed.
            if (fingerprintAttempt && !state.isLoading) {
                fingerprintAttempt = false
                if (state.message == R.string.error_invalid_credentials) {
                    credentialStore.clear()
                    refreshFingerprintButton()
                    toast(R.string.biometric_out_of_date)
                }
            }
        }
    }

    private fun refreshFingerprintButton() {
        binding.biometricButton.isVisible =
            BiometricSupport.isAvailable(requireContext()) && credentialStore.isEnabled()
    }

    private fun signInWithFingerprint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val cipher = credentialStore.createDecryptCipher()
        if (cipher == null) {
            // Fingerprints changed on the device or the saved data is unreadable.
            refreshFingerprintButton()
            toast(R.string.biometric_reset_needed)
            return
        }
        BiometricAuth.authenticate(
            context = requireContext(),
            title = getString(R.string.biometric_login_title),
            subtitle = getString(R.string.biometric_login_subtitle),
            negativeText = getString(R.string.biometric_cancel),
            cipher = cipher,
            onSuccess = { unlocked ->
                if (_binding != null) {
                    val credentials = credentialStore.decrypt(unlocked)
                    if (credentials == null) {
                        credentialStore.clear()
                        refreshFingerprintButton()
                        toast(R.string.biometric_reset_needed)
                    } else {
                        fingerprintAttempt = true
                        viewModel.signIn(emailInput = credentials.email, password = credentials.password)
                    }
                }
            },
            onFailure = { message ->
                if (_binding != null && message != null) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun toast(message: Int) {
        if (_binding != null) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun submit() {
        viewModel.signIn(
            emailInput = binding.emailInput.text.toString(),
            password = binding.passwordInput.text.toString(),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
