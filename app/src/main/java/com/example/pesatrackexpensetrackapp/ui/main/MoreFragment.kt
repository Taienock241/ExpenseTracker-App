package com.example.pesatrackexpensetrackapp.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {
    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.mainViewModelFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        styleRow(binding.categoriesButton, "▦", R.string.more_categories, R.string.more_categories_detail)
        styleRow(binding.accountsButton, "◇", R.string.more_accounts, R.string.more_accounts_detail)
        styleRow(binding.goalsButton, "◎", R.string.more_goals, R.string.more_goals_detail)
        styleRow(binding.recurringButton, "↻", R.string.more_recurring, R.string.more_recurring_detail)
        styleRow(binding.notificationsButton, "♧", R.string.more_notifications, R.string.more_notifications_detail)
        styleRow(binding.profileButton, "●", R.string.more_profile, R.string.more_profile_detail)
        binding.categoriesButton.setOnClickListener { findNavController().navigate(R.id.categoriesFragment) }
        binding.accountsButton.setOnClickListener { findNavController().navigate(R.id.accountsFragment) }
        binding.goalsButton.setOnClickListener { findNavController().navigate(R.id.goalsFragment) }
        binding.recurringButton.setOnClickListener { findNavController().navigate(R.id.recurringFragment) }
        binding.notificationsButton.setOnClickListener { findNavController().navigate(R.id.notificationsFragment) }
        binding.profileButton.setOnClickListener { findNavController().navigate(R.id.profileFragment) }
        binding.signOutButton.setOnClickListener { viewModel.signOut() }
    }

    private fun styleRow(view: TextView, icon: String, titleId: Int, detailId: Int) {
        val title = getString(titleId)
        val detail = getString(detailId)
        val text = SpannableStringBuilder("$icon   $title\n      $detail")
        val detailStart = text.indexOf(detail)
        text.setSpan(StyleSpan(Typeface.BOLD), 0, detailStart - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(RelativeSizeSpan(0.78f), detailStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_muted)), detailStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.text = text
        view.maxLines = 2
        view.setLineSpacing(dp(2).toFloat(), 1f)
        view.contentDescription = "$title. $detail"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
