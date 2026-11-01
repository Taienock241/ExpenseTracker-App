package com.example.pesatrackexpensetrackapp.ui.auth

data class AuthUiState(
    val isLoading: Boolean = false,
    val nameError: Int? = null,
    val emailError: Int? = null,
    val passwordError: Int? = null,
    val confirmPasswordError: Int? = null,
    val message: Int? = null,
    val messageIsSuccess: Boolean = false,
)
