package com.example.pesatrackexpensetrackapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.pesatrackexpensetrackapp.databinding.ActivityMainBinding
import com.example.pesatrackexpensetrackapp.ui.auth.AuthActivity
import com.example.pesatrackexpensetrackapp.ui.main.MainViewModel

class MainActivity : FragmentActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels {
        (application as PesaTrackApp).container.mainViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as PesaTrackApp).container
        if (container.authRepository.currentUser() == null) {
            openAuth()
            return
        }

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
            event.getContentIfNotHandled()?.let { openAuth() }
        }
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
            button.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (selected) R.color.forest_700 else R.color.text_muted,
                ),
            )
            button.isSelected = selected
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
