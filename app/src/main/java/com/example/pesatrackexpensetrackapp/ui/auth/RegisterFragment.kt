package com.example.pesatrackexpensetrackapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.authViewModelFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.clearFeedback()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.signInButton.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
        binding.createAccountActionButton.setOnClickListener {
            viewModel.register(
                nameInput = binding.nameInput.text.toString(),
                emailInput = binding.emailInput.text.toString(),
                password = binding.passwordInput.text.toString(),
                confirmation = binding.confirmPasswordInput.text.toString(),
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.nameInput.error = state.nameError?.let(::getString)
            binding.emailInput.error = state.emailError?.let(::getString)
            binding.passwordInput.error = state.passwordError?.let(::getString)
            binding.confirmPasswordInput.error = state.confirmPasswordError?.let(::getString)
            binding.messageText.isVisible = state.message != null
            state.message?.let(binding.messageText::setText)
            binding.messageText.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (state.messageIsSuccess) R.color.forest_700 else R.color.coral,
                ),
            )
            binding.loadingIndicator.isVisible = state.isLoading
            binding.createAccountActionButton.isEnabled = !state.isLoading
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
