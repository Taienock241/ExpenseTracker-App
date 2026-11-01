package com.example.pesatrackexpensetrackapp.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue

class FirestoreUserRepository(
    private val firestore: FirebaseFirestore,
) : UserRepository {
    override fun createProfile(
        uid: String,
        displayName: String,
        email: String,
        callback: (RepositoryResult) -> Unit,
    ) {
        val profile = mapOf(
            "displayName" to displayName,
            "email" to email,
            "currency" to "KES",
            "setupVersion" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        firestore.collection("users").document(uid).set(profile)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(RepositoryResult.Success)
                } else {
                    val reason = if ((task.exception as? FirebaseFirestoreException)?.code ==
                        FirebaseFirestoreException.Code.UNAVAILABLE
                    ) {
                        RepositoryFailure.NETWORK
                    } else {
                        RepositoryFailure.UNKNOWN
                    }
                    callback(RepositoryResult.Failure(reason))
                }
            }
    }
}
