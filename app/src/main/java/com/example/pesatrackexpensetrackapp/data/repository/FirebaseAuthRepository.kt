package com.example.pesatrackexpensetrackapp.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {
    override fun currentUser(): AuthUser? = firebaseAuth.currentUser?.toDomainUser()

    override fun signIn(email: String, password: String, callback: (AuthResult) -> Unit) {
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                val user = if (task.isSuccessful) task.result?.user else null
                if (task.isSuccessful && user != null) {
                    callback(AuthResult.Success(user.toDomainUser()))
                } else {
                    callback(AuthResult.Failure(mapFailure(task.exception, isSignIn = true)))
                }
            }
    }

    override fun register(
        name: String,
        email: String,
        password: String,
        callback: (AuthResult) -> Unit,
    ) {
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                val user = if (task.isSuccessful) task.result?.user else null
                if (!task.isSuccessful || user == null) {
                    callback(AuthResult.Failure(mapFailure(task.exception, isSignIn = false)))
                    return@addOnCompleteListener
                }

                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                user.updateProfile(profile).addOnCompleteListener {
                    callback(AuthResult.Success(user.toDomainUser(name.trim())))
                }
            }
    }

    override fun sendPasswordReset(email: String, callback: (RepositoryResult) -> Unit) {
        firebaseAuth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                callback(RepositoryResult.Success)
            } else {
                val reason = if (task.exception.isNetworkFailure()) {
                    RepositoryFailure.NETWORK
                } else {
                    RepositoryFailure.UNKNOWN
                }
                callback(RepositoryResult.Failure(reason))
            }
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    override fun updateDisplayName(name: String, callback: (RepositoryResult) -> Unit) {
        val user = firebaseAuth.currentUser
        if (user == null) {
            callback(RepositoryResult.Failure(RepositoryFailure.UNKNOWN))
            return
        }
        val profile = UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
        user.updateProfile(profile).addOnCompleteListener { task ->
            if (task.isSuccessful) callback(RepositoryResult.Success)
            else callback(RepositoryResult.Failure(if (task.exception.isNetworkFailure()) RepositoryFailure.NETWORK else RepositoryFailure.UNKNOWN))
        }
    }

    private fun mapFailure(error: Exception?, isSignIn: Boolean): AuthFailure {
        if (error.isNetworkFailure()) return AuthFailure.NETWORK

        val errorCode = (error as? FirebaseAuthException)?.errorCode
        return when {
            isSignIn -> AuthFailure.INVALID_CREDENTIALS
            errorCode == "ERROR_EMAIL_ALREADY_IN_USE" -> AuthFailure.EMAIL_ALREADY_IN_USE
            errorCode == "ERROR_WEAK_PASSWORD" -> AuthFailure.WEAK_PASSWORD
            else -> AuthFailure.UNKNOWN
        }
    }

    private fun FirebaseUser.toDomainUser(fallbackName: String = ""): AuthUser = AuthUser(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName?.takeIf(String::isNotBlank) ?: fallbackName,
    )

    private fun Exception?.isNetworkFailure(): Boolean =
        this is FirebaseNetworkException ||
            (this as? FirebaseAuthException)?.errorCode == "ERROR_NETWORK_REQUEST_FAILED"
}
