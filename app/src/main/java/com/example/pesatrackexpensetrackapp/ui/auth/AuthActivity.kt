package com.example.pesatrackexpensetrackapp.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.databinding.ActivityAuthBinding
import com.example.pesatrackexpensetrackapp.MainActivity

class AuthActivity : FragmentActivity() {
    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels {
        (application as PesaTrackApp).container.authViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.authenticated.observe(this) { event ->
            event.getContentIfNotHandled()?.let { openMainApp() }
        }
    }

    private fun openMainApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }
}
