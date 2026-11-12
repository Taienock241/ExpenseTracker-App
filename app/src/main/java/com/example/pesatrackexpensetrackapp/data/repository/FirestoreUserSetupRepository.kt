package com.example.pesatrackexpensetrackapp.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class FirestoreUserSetupRepository(
    private val firestore: FirebaseFirestore,
) : UserSetupRepository {
    override fun ensureDefaultsSeeded(
        uid: String,
        displayName: String,
        email: String,
        callback: (RepositoryResult) -> Unit,
    ) {
        val userReference = firestore.collection("users").document(uid)
        userReference.get().addOnCompleteListener { profileTask ->
            if (!profileTask.isSuccessful) {
                callback(RepositoryResult.Failure(profileTask.exception.toRepositoryFailure()))
                return@addOnCompleteListener
            }

            val profile = profileTask.result
            val profileMissing = profile == null || !profile.exists()

            if (!profileMissing && (profile?.getLong("setupVersion") ?: 0L) >= 1L) {
                callback(RepositoryResult.Success)
                return@addOnCompleteListener
            }

            val batch = firestore.batch()
            val categories = userReference.collection("categories")
            DefaultData.categories.forEach { category ->
                batch.set(
                    categories.document(category.id),
                    mapOf(
                        "name" to category.name,
                        "type" to category.type,
                        "iconKey" to category.iconKey,
                        "colorHex" to category.colorHex,
                        "builtIn" to true,
                        "archived" to false,
                        "sortOrder" to category.sortOrder,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }

            batch.set(
                userReference.collection("accounts").document(DefaultData.CASH_ACCOUNT_ID),
                mapOf(
                    "name" to "Cash",
                    "type" to "CASH",
                    "openingBalanceMinor" to 0L,
                    "archived" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            if (profileMissing) {
                // Names must be 2-40 characters to satisfy the Firestore rules.
                val safeName = displayName.trim().ifBlank { email.substringBefore('@') }
                    .take(40).let { if (it.length >= 2) it else "User" }
                batch.set(
                    userReference,
                    mapOf(
                        "displayName" to safeName,
                        "email" to email,
                        "currency" to "KES",
                        "setupVersion" to 1,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            } else {
                batch.update(userReference, "setupVersion", 1)
            }
            batch.commit().addOnCompleteListener { seedTask ->
                if (seedTask.isSuccessful) {
                    callback(RepositoryResult.Success)
                } else {
                    callback(RepositoryResult.Failure(seedTask.exception.toRepositoryFailure()))
                }
            }
        }
    }

    private fun Exception?.toRepositoryFailure(): RepositoryFailure =
        if (this is FirebaseFirestoreException &&
            (code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
        ) {
            RepositoryFailure.NETWORK
        } else {
            RepositoryFailure.UNKNOWN
        }
}
