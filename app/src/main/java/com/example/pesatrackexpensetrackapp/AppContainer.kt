package com.example.pesatrackexpensetrackapp

import androidx.lifecycle.ViewModelProvider
import com.example.pesatrackexpensetrackapp.data.repository.AuthRepository
import com.example.pesatrackexpensetrackapp.data.repository.FirebaseAuthRepository
import com.example.pesatrackexpensetrackapp.data.repository.FirestoreUserRepository
import com.example.pesatrackexpensetrackapp.data.repository.FirestoreUserSetupRepository
import com.example.pesatrackexpensetrackapp.data.repository.FirestoreFinanceRepository
import com.example.pesatrackexpensetrackapp.data.repository.FinanceRepository
import com.example.pesatrackexpensetrackapp.data.repository.UserRepository
import com.example.pesatrackexpensetrackapp.data.repository.UserSetupRepository
import com.example.pesatrackexpensetrackapp.ui.main.FinanceViewModel
import com.example.pesatrackexpensetrackapp.ui.auth.AuthViewModel
import com.example.pesatrackexpensetrackapp.ui.main.MainViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer {
    val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())
    val userRepository: UserRepository = FirestoreUserRepository(FirebaseFirestore.getInstance())
    val userSetupRepository: UserSetupRepository =
        FirestoreUserSetupRepository(FirebaseFirestore.getInstance())
    val financeRepository: FinanceRepository = FirestoreFinanceRepository(FirebaseFirestore.getInstance())

    val authViewModelFactory: ViewModelProvider.Factory = AuthViewModel.Factory(
        authRepository = authRepository,
        userRepository = userRepository,
        userSetupRepository = userSetupRepository,
    )

    val mainViewModelFactory: ViewModelProvider.Factory = MainViewModel.Factory(authRepository)
    val financeViewModelFactory: ViewModelProvider.Factory = FinanceViewModel.Factory(authRepository, financeRepository)
}
