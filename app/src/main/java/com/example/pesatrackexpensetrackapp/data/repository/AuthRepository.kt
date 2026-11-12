package com.example.pesatrackexpensetrackapp.data.repository

data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
)

enum class AuthFailure {
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    INVALID_CREDENTIALS,
    NETWORK,
    UNKNOWN,
}

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class Failure(val reason: AuthFailure) : AuthResult
}

sealed interface RepositoryResult {
    data object Success : RepositoryResult
    data class Failure(val reason: RepositoryFailure) : RepositoryResult
}

enum class RepositoryFailure {
    NETWORK,
    UNKNOWN,
}

interface AuthRepository {
    fun currentUser(): AuthUser?
    fun signIn(email: String, password: String, callback: (AuthResult) -> Unit)
    fun register(name: String, email: String, password: String, callback: (AuthResult) -> Unit)
    fun sendPasswordReset(email: String, callback: (RepositoryResult) -> Unit)
    fun updateDisplayName(name: String, callback: (RepositoryResult) -> Unit)
    fun signOut()
}

interface UserRepository {
    fun createProfile(
        uid: String,
        displayName: String,
        email: String,
        callback: (RepositoryResult) -> Unit,
    )
}

interface UserSetupRepository {
    /**
     * Seeds default categories/accounts. If the user's profile document is missing (for example registration
     * was interrupted after the auth account was created) it is recreated from [displayName] and [email].
     */
    fun ensureDefaultsSeeded(
        uid: String,
        displayName: String,
        email: String,
        callback: (RepositoryResult) -> Unit,
    )
}
