package com.example.pesatrackexpensetrackapp.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pesatrackexpensetrackapp.data.repository.AuthRepository
import com.example.pesatrackexpensetrackapp.data.repository.AuthUser
import com.example.pesatrackexpensetrackapp.utils.Event

class MainViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _currentUser = MutableLiveData(authRepository.currentUser())
    val currentUserState: LiveData<AuthUser?> = _currentUser
    val currentUser: AuthUser? get() = _currentUser.value

    private val _signedOut = MutableLiveData<Event<Unit>>()
    val signedOut: LiveData<Event<Unit>> = _signedOut

    fun signOut() {
        authRepository.signOut()
        _signedOut.value = Event(Unit)
    }

    fun refreshCurrentUser() {
        _currentUser.value = authRepository.currentUser()
    }

    class Factory(
        private val authRepository: AuthRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
