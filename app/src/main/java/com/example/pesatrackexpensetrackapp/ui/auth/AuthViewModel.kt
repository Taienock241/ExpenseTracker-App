package com.example.pesatrackexpensetrackapp.ui.auth

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.data.repository.AuthFailure
import com.example.pesatrackexpensetrackapp.data.repository.AuthRepository
import com.example.pesatrackexpensetrackapp.data.repository.AuthResult
import com.example.pesatrackexpensetrackapp.data.repository.RepositoryFailure
import com.example.pesatrackexpensetrackapp.data.repository.RepositoryResult
import com.example.pesatrackexpensetrackapp.data.repository.UserRepository
import com.example.pesatrackexpensetrackapp.data.repository.UserSetupRepository
import com.example.pesatrackexpensetrackapp.utils.Event

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userSetupRepository: UserSetupRepository,
) : ViewModel() {
    private val _state = MutableLiveData(AuthUiState())
    val state: LiveData<AuthUiState> = _state

    private val _authenticated = MutableLiveData<Event<Unit>>()
    val authenticated: LiveData<Event<Unit>> = _authenticated

    fun clearFeedback() {
        _state.value = AuthUiState()
    }

    fun signIn(emailInput: String, password: String) {
        val email = emailInput.trim()
        val emailError = if (isValidEmail(email)) null else R.string.error_email_required
        val passwordError = if (password.isBlank()) R.string.error_password_required else null
        if (emailError != null || passwordError != null) {
            _state.value = AuthUiState(emailError = emailError, passwordError = passwordError)
            return
        }

        _state.value = AuthUiState(isLoading = true)
        authRepository.signIn(email, password) { result ->
            when (result) {
                is AuthResult.Success -> finishAuthentication(result.user.uid, result.user.displayName, result.user.email)
                is AuthResult.Failure -> _state.value = AuthUiState(
                    message = when (result.reason) {
                        AuthFailure.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
                        AuthFailure.NETWORK -> R.string.error_network
                        else -> R.string.error_generic
                    },
                )
            }
        }
    }

    fun register(nameInput: String, emailInput: String, password: String, confirmation: String) {
        val name = nameInput.trim()
        val email = emailInput.trim()
        val nameError = if (name.length in 2..40) null else R.string.error_name_required
        val emailError = if (isValidEmail(email)) null else R.string.error_email_required
        val passwordError = if (isValidPassword(password)) null else R.string.error_password_rules
        val confirmationError = if (password == confirmation) null else R.string.error_password_mismatch

        if (nameError != null || emailError != null || passwordError != null || confirmationError != null) {
            _state.value = AuthUiState(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmationError,
            )
            return
        }

        _state.value = AuthUiState(isLoading = true)
        authRepository.register(name, email, password) { result ->
            when (result) {
                is AuthResult.Failure -> _state.value = AuthUiState(
                    message = when (result.reason) {
                        AuthFailure.EMAIL_ALREADY_IN_USE -> R.string.error_email_exists
                        AuthFailure.WEAK_PASSWORD -> R.string.error_password_rules
                        AuthFailure.NETWORK -> R.string.error_network
                        else -> R.string.error_generic
                    },
                )
                is AuthResult.Success -> userRepository.createProfile(
                    uid = result.user.uid,
                    displayName = name,
                    email = email,
                ) { profileResult ->
                    when (profileResult) {
                        RepositoryResult.Success -> finishAuthentication(result.user.uid, name, email)
                        is RepositoryResult.Failure -> {
                            authRepository.signOut()
                            _state.value = AuthUiState(message = R.string.error_profile_setup)
                        }
                    }
                }
            }
        }
    }

    fun sendPasswordReset(emailInput: String) {
        val email = emailInput.trim()
        if (!isValidEmail(email)) {
            _state.value = AuthUiState(emailError = R.string.error_email_required)
            return
        }

        _state.value = AuthUiState(isLoading = true)
        authRepository.sendPasswordReset(email) { result ->
            when (result) {
                RepositoryResult.Success -> _state.value = AuthUiState(
                    message = R.string.message_reset_sent,
                    messageIsSuccess = true,
                )
                is RepositoryResult.Failure -> _state.value = AuthUiState(
                    message = if (result.reason == RepositoryFailure.NETWORK) {
                        R.string.error_network
                    } else {
                        R.string.error_generic
                    },
                )
            }
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun isValidPassword(password: String): Boolean =
        password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }

    private fun finishAuthentication(uid: String, displayName: String, email: String) {
        userSetupRepository.ensureDefaultsSeeded(uid, displayName, email) { result ->
            when (result) {
                RepositoryResult.Success -> {
                    _state.value = AuthUiState()
                    _authenticated.value = Event(Unit)
                }
                is RepositoryResult.Failure -> {
                    authRepository.signOut()
                    _state.value = AuthUiState(message = R.string.error_default_setup)
                }
            }
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository,
        private val userSetupRepository: UserSetupRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                return AuthViewModel(authRepository, userRepository, userSetupRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
