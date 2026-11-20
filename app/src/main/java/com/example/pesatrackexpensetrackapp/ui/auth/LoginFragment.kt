package com.example.pesatrackexpensetrackapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.MainActivity
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.databinding.FragmentLoginBinding
import com.example.pesatrackexpensetrackapp.utils.BiometricUnlockManager
import com.example.pesatrackexpensetrackapp.utils.SessionManager

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.authViewModelFactory
    }

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
        val app = requireActivity().application as PesaTrackApp
        val currentUser = app.container.authRepository.currentUser()
        val canUseFingerprint = currentUser != null &&
            SessionManager(requireContext()).isExpired() &&
            BiometricUnlockManager.isEnabled(requireContext(), currentUser.uid)
        binding.fingerprintUnlockButton.isVisible = canUseFingerprint
        if (canUseFingerprint) binding.emailInput.setText(currentUser?.email)
        binding.fingerprintUnlockButton.setOnClickListener {
            val user = app.container.authRepository.currentUser() ?: return@setOnClickListener
            BiometricUnlockManager.authenticate(
                requireActivity() as androidx.fragment.app.FragmentActivity,
                user.uid,
                "Unlock Expense Tracker",
                "Use your fingerprint to continue.",
                onSuccess = {
                    SessionManager(requireContext()).recordActivity()
                    startActivity(
                        Intent(requireContext(), MainActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                        ),
                    )
                    requireActivity().finish()
                },
                onError = { message -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show() },
            )
        }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
        binding.forgotPasswordButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgot_password)
        }
        binding.signInActionButton.setOnClickListener {
            viewModel.signIn(
                emailInput = binding.emailInput.text.toString(),
                password = binding.passwordInput.text.toString(),
            )
        }

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
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
