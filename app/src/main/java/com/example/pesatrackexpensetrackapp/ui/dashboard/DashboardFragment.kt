package com.example.pesatrackexpensetrackapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.databinding.FragmentDashboardBinding
import com.example.pesatrackexpensetrackapp.ui.main.MainViewModel
import com.example.pesatrackexpensetrackapp.ui.main.FinanceViewModel
import com.example.pesatrackexpensetrackapp.utils.renderProfileAvatar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {
    private var hideSensitiveAmounts = false
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.mainViewModelFactory
    }
    private val financeViewModel: FinanceViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.financeViewModelFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val amountPreferences = requireContext().getSharedPreferences("dashboard_preferences", 0)
        hideSensitiveAmounts = amountPreferences.getBoolean("hide_sensitive_amounts", false)
        updateBalanceVisibilityControl()
        binding.dashboardBalanceVisibility.setOnClickListener {
            hideSensitiveAmounts = !hideSensitiveAmounts
            amountPreferences.edit().putBoolean("hide_sensitive_amounts", hideSensitiveAmounts).apply()
            updateBalanceVisibilityControl()
            renderFinance()
        }
        mainViewModel.currentUserState.observe(viewLifecycleOwner) { updateGreeting() }
        updateGreeting()
        binding.dashboardProfileAvatar.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        binding.addTransactionButton.setOnClickListener {
            findNavController().navigate(R.id.transactionFormFragment)
        }
        binding.openBudgetsButton.setOnClickListener {
            findNavController().navigate(R.id.budgetsFragment)
        }
        binding.dashboardBudgetsCard.setOnClickListener {
            findNavController().navigate(R.id.budgetsFragment)
        }
        binding.dashboardAccountsCard.setOnClickListener {
            findNavController().navigate(R.id.accountsFragment)
        }
        binding.dashboardReportsCard.setOnClickListener {
            findNavController().navigate(R.id.reportsFragment)
        }
        binding.dashboardTransactionsCard.setOnClickListener {
            findNavController().navigate(R.id.transactionsFragment)
        }
        binding.viewAllTransactions.setOnClickListener {
            findNavController().navigate(R.id.transactionsFragment)
        }
        financeViewModel.transactions.observe(viewLifecycleOwner) { renderFinance() }
        financeViewModel.accounts.observe(viewLifecycleOwner) { renderFinance() }
        financeViewModel.budgets.observe(viewLifecycleOwner) { renderFinance() }
        financeViewModel.categories.observe(viewLifecycleOwner) { renderFinance() }
    }

    private fun renderFinance() {
        val transactions = financeViewModel.transactions.value.orEmpty()
        val accounts = financeViewModel.accounts.value.orEmpty().filterNot { it.archived }
        val month = Calendar.getInstance()
        val thisMonth = transactions.filter { transaction ->
            Calendar.getInstance().apply { timeInMillis = transaction.occurredAtMillis }.let {
                it.get(Calendar.YEAR) == month.get(Calendar.YEAR) && it.get(Calendar.MONTH) == month.get(Calendar.MONTH)
            }
        }
        val income = thisMonth.filter { it.type == "INCOME" }.sumOf { it.amountMinor }
        val expenses = thisMonth.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
        val budgets = financeViewModel.budgets.value.orEmpty().filter { it.month == financeViewModel.currentMonth() }
        binding.dashboardBudgetCount.text = budgets.size.toString()
        binding.dashboardAccountCount.text = accounts.size.toString()
        binding.dashboardTransactionCount.text = thisMonth.size.toString()
        binding.dashboardReportValue.text = displayMoney(income - expenses)
        val balance = accounts.sumOf { account ->
            account.openingBalanceMinor + transactions.filter { it.accountId == account.id }
                .sumOf { if (it.type == "INCOME") it.amountMinor else -it.amountMinor }
        }
        binding.dashboardBalance.text = displayMoney(balance)
        binding.dashboardIncome.text = displayMoney(income)
        binding.dashboardExpenses.text = displayMoney(expenses)

        val budgetLimit = budgets.sumOf { it.amountLimitMinor }
        // Compare only spending in budgeted categories against the category limits.
        val budgetedCategoryIds = budgets.map { it.categoryId }.toSet()
        val budgetedSpending = thisMonth
            .filter { it.type == "EXPENSE" && it.categoryId in budgetedCategoryIds }
            .sumOf { it.amountMinor }
        binding.openBudgetsButton.text = if (budgetLimit == 0L) {
            getString(R.string.dashboard_budget_hint)
        } else {
            if (hideSensitiveAmounts) "Monthly budget  ·  Amounts hidden"
            else "Monthly budget  ·  ${money(budgetedSpending)} of ${money(budgetLimit)} used"
        }

        val container = binding.dashboardRecentContainer
        container.removeAllViews()
        if (transactions.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.dashboard_empty)
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
                setPadding(dp(17), dp(17), dp(17), dp(17))
                setBackgroundResource(R.drawable.bg_card)
            })
        } else {
            transactions.take(4).forEach { transaction ->
                val category = financeViewModel.categories.value.orEmpty().firstOrNull { it.id == transaction.categoryId }?.name ?: "Category"
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    setBackgroundResource(R.drawable.bg_card)
                }
                val headline = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                headline.addView(TextView(requireContext()).apply {
                    text = transaction.title
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                headline.addView(TextView(requireContext()).apply {
                    text = (if (transaction.type == "INCOME") "+ " else "− ") + displayMoney(transaction.amountMinor)
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(requireContext(), if (transaction.type == "INCOME") R.color.green_500 else R.color.text_primary))
                })
                row.addView(headline)
                row.addView(TextView(requireContext()).apply {
                    text = "$category  ·  ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(transaction.occurredAtMillis))}"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
                    setPadding(0, dp(4), 0, 0)
                })
                container.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun updateGreeting() {
        val user = mainViewModel.currentUser
        val userName = user?.displayName?.takeIf(String::isNotBlank)
            ?: user?.email?.substringBefore('@')
            ?: "there"
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour in 5..11 -> "morning"
            hour in 12..16 -> "afternoon"
            hour in 17..21 -> "evening"
            else -> "night"
        }
        binding.dashboardGreeting.text = getString(R.string.dashboard_hello, timeGreeting, userName)
        renderProfileAvatar(
            requireContext(),
            user?.uid,
            user?.displayName,
            user?.email,
            binding.dashboardProfileAvatar,
            binding.dashboardProfileInitial,
            binding.dashboardProfileImage,
        )
    }

    private fun updateBalanceVisibilityControl() {
        binding.dashboardBalanceVisibility.setImageResource(
            if (hideSensitiveAmounts) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
        )
        binding.dashboardBalanceVisibility.contentDescription =
            if (hideSensitiveAmounts) "Show balances" else "Hide balances"
    }

    private fun displayMoney(minor: Long): String =
        if (hideSensitiveAmounts) "KSh  ••••••" else money(minor)

    override fun onResume() {
        super.onResume()
        if (_binding != null) updateGreeting()
    }

    private fun money(minor: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-KE"))
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return "KSh ${formatter.format(minor / 100.0)}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
