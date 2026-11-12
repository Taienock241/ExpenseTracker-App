package com.example.pesatrackexpensetrackapp

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.pesatrackexpensetrackapp.databinding.ActivityMainBinding
import com.example.pesatrackexpensetrackapp.ui.auth.AuthActivity
import com.example.pesatrackexpensetrackapp.ui.main.MainViewModel
import com.example.pesatrackexpensetrackapp.utils.SessionManager

class MainActivity : FragmentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sessionManager: SessionManager
    private val sessionHandler = Handler(Looper.getMainLooper())
    private var sessionActive = false
    private val sessionExpiryCheck = object : Runnable {
        override fun run() {
            if (!sessionActive) return
            if (sessionManager.isExpired()) {
                expireSession()
            } else {
                scheduleSessionExpiryCheck()
            }
        }
    }
    private val viewModel: MainViewModel by viewModels {
        (application as PesaTrackApp).container.mainViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        val container = (application as PesaTrackApp).container
        if (container.authRepository.currentUser() == null) {
            sessionManager.clear()
            openAuth()
            return
        }

        if (sessionManager.isExpired()) {
            sessionManager.clear()
            container.authRepository.signOut()
            openAuth()
            return
        }

        sessionManager.recordActivity()
        sessionActive = true

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navHost = supportFragmentManager.findFragmentById(R.id.mainNavHost) as NavHostFragment
        navController = navHost.navController

        val destinations = listOf(
            binding.navHomeButton to R.id.dashboardFragment,
            binding.navTransactionsButton to R.id.transactionsFragment,
            binding.navBudgetsButton to R.id.budgetsFragment,
            binding.navReportsButton to R.id.reportsFragment,
            binding.navMoreButton to R.id.moreFragment,
        )
        destinations.forEach { (button, destination) ->
            button.setOnClickListener { navigate(destination) }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavigationSelection(destinations, destination.id)
            binding.bottomNavigation.visibility = if (destinations.any { it.second == destination.id }) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        viewModel.signedOut.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                sessionActive = false
                sessionHandler.removeCallbacks(sessionExpiryCheck)
                sessionManager.clear()
                openAuth()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionActive) {
            if (sessionManager.isExpired()) expireSession() else {
                sessionManager.recordActivity()
                scheduleSessionExpiryCheck()
            }
        }
    }

    override fun onPause() {
        sessionHandler.removeCallbacks(sessionExpiryCheck)
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) registerUserActivity()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) registerUserActivity()
        return super.dispatchKeyEvent(event)
    }

    private fun registerUserActivity() {
        if (!sessionActive) return
        if (sessionManager.isExpired()) {
            expireSession()
        } else {
            sessionManager.recordActivity()
            scheduleSessionExpiryCheck()
        }
    }

    private fun scheduleSessionExpiryCheck() {
        sessionHandler.removeCallbacks(sessionExpiryCheck)
        sessionHandler.postDelayed(
            sessionExpiryCheck,
            SessionManager.SESSION_TIMEOUT_MILLIS,
        )
    }

    private fun expireSession() {
        if (!sessionActive) return
        sessionActive = false
        sessionHandler.removeCallbacks(sessionExpiryCheck)
        sessionManager.clear()
        (application as PesaTrackApp).container.authRepository.signOut()
        openAuth()
    }

    private fun navigate(destination: Int) {
        if (navController.currentDestination?.id != destination) {
            navController.navigate(destination)
        }
    }

    private fun updateNavigationSelection(
        destinations: List<Pair<Button, Int>>,
        currentDestination: Int,
    ) {
        destinations.forEach { (button, destination) ->
            val selected = destination == currentDestination
            button.isSelected = selected
            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun openAuth() {
        startActivity(
            Intent(this, AuthActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }
}
