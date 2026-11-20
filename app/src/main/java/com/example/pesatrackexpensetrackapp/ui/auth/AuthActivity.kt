package com.example.pesatrackexpensetrackapp.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.databinding.ActivityAuthBinding
import com.example.pesatrackexpensetrackapp.MainActivity
import com.example.pesatrackexpensetrackapp.utils.AppTypography
import com.example.pesatrackexpensetrackapp.utils.BiometricUnlockManager
import com.example.pesatrackexpensetrackapp.utils.SessionManager
import com.example.pesatrackexpensetrackapp.utils.applySystemBarInsets

class AuthActivity : FragmentActivity() {
    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels {
        (application as PesaTrackApp).container.authViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTypography.installOnFragments(this)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppTypography.apply(binding.root)
        binding.root.applySystemBarInsets()

        viewModel.authenticated.observe(this) { event ->
            event.getContentIfNotHandled()?.let { openMainApp() }
        }

        val user = (application as PesaTrackApp).container.authRepository.currentUser()
        val sessionManager = SessionManager(this)
        if (savedInstanceState == null && user != null && sessionManager.isExpired() &&
            BiometricUnlockManager.isEnabled(this, user.uid)
        ) {
            BiometricUnlockManager.authenticate(
                this,
                user.uid,
                "Unlock Expense Tracker",
                "Use your fingerprint to continue.",
                onSuccess = { openMainApp() },
                onError = { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() },
            )
        }
    }

    private fun openMainApp() {
        SessionManager(this).recordActivity()
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }
}
