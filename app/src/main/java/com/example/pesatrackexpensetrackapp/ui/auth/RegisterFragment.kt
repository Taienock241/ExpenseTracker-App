package com.example.pesatrackexpensetrackapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
        binding.createAccountActionButton.setOnClickListener { submit() }
        binding.confirmPasswordInput.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        // Live feedback while typing: requirement checklist and password match.
        binding.passwordInput.addOnTextChanged { password ->
            binding.passwordRequirements.update(password?.toString().orEmpty())
            if (binding.confirmPasswordInput.text.isNotEmpty()) showMismatchIfAny()
        }
        binding.confirmPasswordInput.addOnTextChanged { showMismatchIfAny() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            // The backend stores a single display name, so name errors are shown under the first name.
            binding.firstNameInput.error = state.nameError?.let(::getString)
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
            // The spinner replaces the label while the request is running.
            binding.createAccountActionButton.text =
                if (state.isLoading) "" else getString(R.string.action_sign_up)
        }
    }

    private fun showMismatchIfAny() {
        val password = binding.passwordInput.text.toString()
        val confirmation = binding.confirmPasswordInput.text.toString()
        binding.confirmPasswordInput.error =
            if (confirmation.isNotEmpty() && confirmation != password) getString(R.string.error_password_mismatch) else null
    }

    private fun submit() {
        val firstName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        if (firstName.isEmpty() || lastName.isEmpty()) {
            binding.firstNameInput.error = if (firstName.isEmpty()) getString(R.string.error_first_name_required) else null
            binding.lastNameInput.error = if (lastName.isEmpty()) getString(R.string.error_last_name_required) else null
            return
        }
        binding.lastNameInput.error = null
        // The existing backend profile has one display name field, so first and last name are combined.
        viewModel.register(
            nameInput = "$firstName $lastName",
            emailInput = binding.emailInput.text.toString(),
            password = binding.passwordInput.text.toString(),
            confirmation = binding.confirmPasswordInput.text.toString(),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
