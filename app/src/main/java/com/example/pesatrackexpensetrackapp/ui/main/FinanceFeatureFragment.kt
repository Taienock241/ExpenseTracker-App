package com.example.pesatrackexpensetrackapp.ui.main

import android.app.DatePickerDialog
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.pesatrackexpensetrackapp.PesaTrackApp
import com.example.pesatrackexpensetrackapp.R
import com.example.pesatrackexpensetrackapp.data.model.FinanceAccount
import com.example.pesatrackexpensetrackapp.data.model.FinanceCategory
import com.example.pesatrackexpensetrackapp.data.model.FinanceTransaction
import com.example.pesatrackexpensetrackapp.data.model.MonthlyBudget
import com.example.pesatrackexpensetrackapp.data.model.RecurringItem
import com.example.pesatrackexpensetrackapp.data.model.SavingsGoal
import com.example.pesatrackexpensetrackapp.databinding.FragmentFinanceBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FinanceFeatureFragment : Fragment() {
    private var _binding: FragmentFinanceBinding? = null
    private val binding get() = _binding!!
    private val finance: FinanceViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.financeViewModelFactory
    }
    private val main: MainViewModel by activityViewModels {
        (requireActivity().application as PesaTrackApp).container.mainViewModelFactory
    }
    private val screenId get() = findNavController().currentDestination?.id
    private val activeCategories get() = finance.categories.value.orEmpty().filterNot { it.archived }
    private val activeAccounts get() = finance.accounts.value.orEmpty().filterNot { it.archived }
    private var transactionSearchText = ""
    private var transactionTypeFilter = 0
    private var activeDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFinanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val titleId = arguments?.getInt("title") ?: R.string.transactions_title
        binding.financeTitle.setText(titleId)
        binding.financeSubtitle.text = subtitleFor(screenId)
        val rootDestination = screenId in setOf(R.id.transactionsFragment, R.id.budgetsFragment, R.id.reportsFragment)
        binding.financeBackButton.visibility = if (rootDestination) View.GONE else View.VISIBLE
        binding.financeBackButton.setOnClickListener { findNavController().popBackStack() }
        binding.financeAddButton.setOnClickListener { handleAdd() }
        binding.transactionSearchRow.visibility = if (screenId == R.id.transactionsFragment) View.VISIBLE else View.GONE
        binding.transactionTypeFilter.adapter = adapter(listOf("All activity", "Expenses", "Income"))
        binding.transactionTypeFilter.onItemSelectedListener = simpleSelection {
            transactionTypeFilter = binding.transactionTypeFilter.selectedItemPosition
            render()
        }
        binding.transactionSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                transactionSearchText = s?.toString().orEmpty()
                render()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        finance.transactions.observe(viewLifecycleOwner) { render() }
        finance.categories.observe(viewLifecycleOwner) { render() }
        finance.accounts.observe(viewLifecycleOwner) { render() }
        finance.budgets.observe(viewLifecycleOwner) { render() }
        finance.goals.observe(viewLifecycleOwner) { render() }
        finance.recurring.observe(viewLifecycleOwner) { render() }
        finance.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
        finance.saved.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                activeDialog?.dismiss()
                activeDialog = null
                if (screenId == R.id.transactionFormFragment) findNavController().popBackStack()
            }
        }
        finance.profileUpdated.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { main.refreshCurrentUser() }
        }
        main.currentUserState.observe(viewLifecycleOwner) { if (screenId == R.id.profileFragment) render() }
        if (screenId == R.id.transactionFormFragment && savedInstanceState == null) {
            binding.financeAddButton.visibility = View.GONE
            binding.financeSummaryCard.visibility = View.GONE
            binding.financeRowsContainer.addView(text("Add an income or expense to your PesaTrack account.", 14, R.color.text_muted))
            binding.financeRowsContainer.post { showTransactionDialog() }
        }
        render()
    }

    private fun subtitleFor(id: Int?): String = when (id) {
        R.id.transactionsFragment -> "Your income and spending, all in one place."
        R.id.budgetsFragment -> "Plan your monthly spending by category."
        R.id.reportsFragment -> "Understand your money patterns over time."
        R.id.categoriesFragment -> "Organize income and expenses your way."
        R.id.accountsFragment -> "Keep track of cash, bank and M-Pesa balances."
        R.id.goalsFragment -> "Save steadily for the things that matter."
        R.id.recurringFragment -> "Keep regular bills and income in view."
        R.id.notificationsFragment -> "Helpful reminders based on your activity."
        R.id.profileFragment -> "Your account and app preferences."
        else -> "Track and plan your money with confidence."
    }

    private fun render() {
        val rows = _binding?.financeRowsContainer ?: return
        rows.removeAllViews()
        when (screenId) {
            R.id.transactionsFragment -> renderTransactions(rows)
            R.id.transactionFormFragment -> {
                binding.financeSummaryCard.visibility = View.GONE
                binding.transactionSearchRow.visibility = View.GONE
                binding.financeAddButton.visibility = View.VISIBLE
                binding.financeAddButton.text = "＋  Add transaction"
            }
            R.id.budgetsFragment -> renderBudgets(rows)
            R.id.reportsFragment -> renderReports(rows)
            R.id.categoriesFragment -> renderCategories(rows)
            R.id.accountsFragment -> renderAccounts(rows)
            R.id.goalsFragment -> renderGoals(rows)
            R.id.recurringFragment -> renderRecurring(rows)
            R.id.notificationsFragment -> renderNotifications(rows)
            R.id.profileFragment -> renderProfile(rows)
        }
    }

    private fun renderTransactions(rows: LinearLayout) {
        binding.financeSummaryCard.visibility = View.VISIBLE
        binding.transactionSearchRow.visibility = View.VISIBLE
        val monthTransactions = finance.transactions.value.orEmpty().filter { isCurrentMonth(it.occurredAtMillis) }
        val expenses = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
        binding.financeSummaryLabel.text = "THIS MONTH'S SPENDING"
        binding.financeSummaryAmount.text = money(expenses)
        binding.financeSummaryNote.text = "${monthTransactions.size} transactions recorded this month"
        binding.financeAddButton.visibility = View.VISIBLE
        binding.financeAddButton.text = "＋  Add transaction"
        val query = transactionSearchText.trim().lowercase()
        val all = finance.transactions.value.orEmpty().filter { item ->
            val typeMatches = transactionTypeFilter == 0 ||
                (transactionTypeFilter == 1 && item.type == "EXPENSE") ||
                (transactionTypeFilter == 2 && item.type == "INCOME")
            val categoryName = finance.categories.value.orEmpty().firstOrNull { it.id == item.categoryId }?.name.orEmpty()
            typeMatches && (query.isBlank() || item.title.lowercase().contains(query) || item.notes.lowercase().contains(query) || categoryName.lowercase().contains(query))
        }
        section(rows, "Recent activity")
        if (all.isEmpty()) empty(rows, "No transactions yet", "Add your first income or expense to see it here.")
        all.take(100).forEach { item ->
            val category = finance.categories.value.orEmpty().firstOrNull { it.id == item.categoryId }?.name ?: "Category"
            val account = finance.accounts.value.orEmpty().firstOrNull { it.id == item.accountId }?.name ?: "Account"
            card(rows).apply {
                orientation = LinearLayout.VERTICAL
                addView(rowText(item.title, money(item.amountMinor), item.type == "INCOME"))
                addView(text("$category  ·  $account", 12, R.color.text_muted).apply { setPadding(0, dp(5), 0, 0) })
                addView(text(date(item.occurredAtMillis), 11, R.color.text_muted).apply { setPadding(0, dp(3), 0, 0) })
                setOnClickListener { showTransactionActions(item) }
            }
        }
    }

    private fun renderBudgets(rows: LinearLayout) {
        val month = finance.currentMonth()
        val budgets = finance.budgets.value.orEmpty().filter { it.month == month }
        val spent = finance.transactions.value.orEmpty().filter { it.type == "EXPENSE" && isCurrentMonth(it.occurredAtMillis) }.sumOf { it.amountMinor }
        binding.financeSummaryLabel.text = "SPENT THIS MONTH"
        binding.financeSummaryAmount.text = money(spent)
        binding.financeSummaryNote.text = if (budgets.isEmpty()) "Set category limits to stay on track" else "${budgets.size} category limits for $month"
        binding.financeAddButton.visibility = View.VISIBLE
        binding.financeAddButton.text = "＋  Set a budget"
        section(rows, "Category budgets")
        if (budgets.isEmpty()) empty(rows, "No budgets yet", "Choose a category and set a monthly spending limit.")
        budgets.forEach { budget ->
            val category = finance.categories.value.orEmpty().firstOrNull { it.id == budget.categoryId }
            val categorySpent = finance.transactions.value.orEmpty().filter {
                it.type == "EXPENSE" && it.categoryId == budget.categoryId && isCurrentMonth(it.occurredAtMillis)
            }.sumOf { it.amountMinor }
            val card = card(rows)
            card.addView(rowText(category?.name ?: "Category", money(budget.amountLimitMinor), false))
            card.addView(text("${money(categorySpent)} spent  ·  ${money((budget.amountLimitMinor - categorySpent).coerceAtLeast(0))} left", 12, R.color.text_muted).apply { setPadding(0, dp(7), 0, dp(5)) })
            card.addView(ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = ((categorySpent * 100 / budget.amountLimitMinor.coerceAtLeast(1)).toInt()).coerceIn(0, 100)
                progressTintList = ContextCompat.getColorStateList(requireContext(), if (categorySpent > budget.amountLimitMinor) R.color.coral else R.color.forest_600)
            })
        }
    }

    private fun renderReports(rows: LinearLayout) {
        val current = finance.transactions.value.orEmpty().filter { isCurrentMonth(it.occurredAtMillis) }
        val income = current.filter { it.type == "INCOME" }.sumOf { it.amountMinor }
        val expenses = current.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
        binding.financeSummaryLabel.text = "NET CASH FLOW THIS MONTH"
        binding.financeSummaryAmount.text = money(income - expenses)
        binding.financeSummaryNote.text = "Income ${money(income)}  ·  Spending ${money(expenses)}"
        binding.financeAddButton.visibility = View.GONE
        section(rows, "Monthly overview")
        val months = (5 downTo 0).map { offset -> Calendar.getInstance().apply { add(Calendar.MONTH, -offset) } }
        val monthValues = months.map { calendar ->
            val y = calendar.get(Calendar.YEAR); val m = calendar.get(Calendar.MONTH)
            val amount = finance.transactions.value.orEmpty().filter { transaction ->
                Calendar.getInstance().apply { timeInMillis = transaction.occurredAtMillis }.let {
                    it.get(Calendar.YEAR) == y && it.get(Calendar.MONTH) == m && transaction.type == "EXPENSE"
                }
            }.sumOf { it.amountMinor }
            calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()).orEmpty() to amount
        }
        val maxValue = monthValues.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
        monthValues.forEach { (label, amount) ->
            val barRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(6)) }
            barRow.addView(text(label, 12, R.color.text_muted), LinearLayout.LayoutParams(dp(42), -2))
            barRow.addView(ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = (amount * 100 / maxValue).toInt().coerceIn(0, 100)
                progressTintList = ContextCompat.getColorStateList(requireContext(), R.color.forest_600)
            }, LinearLayout.LayoutParams(0, dp(8), 1f))
            barRow.addView(text(money(amount), 11, R.color.text_primary).apply { gravity = android.view.Gravity.END }, LinearLayout.LayoutParams(dp(100), -2))
            rows.addView(barRow)
        }
        section(rows, "Spending by category")
        val categoryTotals = current.filter { it.type == "EXPENSE" }.groupBy { it.categoryId }
            .mapValues { (_, values) -> values.sumOf { it.amountMinor } }.toList().sortedByDescending { it.second }
        if (categoryTotals.isEmpty()) empty(rows, "Nothing to report yet", "Your spending breakdown will appear when you record expenses.")
        categoryTotals.forEach { (id, total) ->
            val name = finance.categories.value.orEmpty().firstOrNull { it.id == id }?.name ?: "Other"
            card(rows).addView(rowText(name, money(total), false))
        }
    }

    private fun renderCategories(rows: LinearLayout) {
        val categories = activeCategories
        binding.financeSummaryLabel.text = "YOUR CATEGORIES"
        binding.financeSummaryAmount.text = categories.size.toString()
        binding.financeSummaryNote.text = "${categories.count { it.type == "EXPENSE" }} expense  ·  ${categories.count { it.type == "INCOME" }} income"
        binding.financeAddButton.visibility = View.VISIBLE
        binding.financeAddButton.text = "＋  Add category"
        listOf("EXPENSE" to "Expense categories", "INCOME" to "Income categories").forEach { (type, label) ->
            section(rows, label)
            val items = categories.filter { it.type == type }.sortedBy { it.sortOrder }
            if (items.isEmpty()) empty(rows, "No categories", "Add a category to organize this activity.")
            items.forEach { category ->
                card(rows).apply {
                    addView(rowText("●  ${category.name}", if (category.builtIn) "Default" else "Manage", false))
                    setOnClickListener { if (!category.builtIn) confirm("Archive ${category.name}?", "This category will no longer appear when recording new activity.") { finance.archiveCategory(category) } }
                }
            }
        }
    }

    private fun renderAccounts(rows: LinearLayout) {
        binding.financeAddButton.visibility = View.VISIBLE
        binding.financeAddButton.text = "＋  Add account"
        val accounts = activeAccounts
        val tx = finance.transactions.value.orEmpty()
        val total = accounts.sumOf { account -> account.openingBalanceMinor + tx.filter { it.accountId == account.id }.sumOf { if (it.type == "INCOME") it.amountMinor else -it.amountMinor } }
        binding.financeSummaryLabel.text = "TOTAL ACCOUNT BALANCE"
        binding.financeSummaryAmount.text = money(total)
        binding.financeSummaryNote.text = "${accounts.size} active ${if (accounts.size == 1) "account" else "accounts"}"
        section(rows, "Your accounts")
        if (accounts.isEmpty()) empty(rows, "No accounts found", "Your Cash account should appear after setup finishes.")
        accounts.forEach { account ->
            val balance = account.openingBalanceMinor + tx.filter { it.accountId == account.id }.sumOf { if (it.type == "INCOME") it.amountMinor else -it.amountMinor }
            card(rows).apply {
                addView(rowText("${account.name}  ·  ${account.type.lowercase().replaceFirstChar { it.uppercase() }}", money(balance), false))
                setOnClickListener { confirm("Archive ${account.name}?", "Archived accounts remain in your history but cannot be selected for new transactions.") { finance.archiveAccount(account) } }
            }
        }
    }

    private fun renderGoals(rows: LinearLayout) {
        val goals = finance.goals.value.orEmpty().filterNot { it.archived }
        val saved = goals.sumOf { it.savedMinor }; val target = goals.sumOf { it.targetMinor }
        binding.financeSummaryLabel.text = "SAVED TOWARD YOUR GOALS"
        binding.financeSummaryAmount.text = money(saved)
        binding.financeSummaryNote.text = "${goals.size} active ${if (goals.size == 1) "goal" else "goals"}  ·  ${money(target)} total target"
        binding.financeAddButton.visibility = View.VISIBLE
        binding.financeAddButton.text = "＋  Create savings goal"
        section(rows, "Your goals")
        if (goals.isEmpty()) empty(rows, "Start with a goal", "Set a target and watch your savings grow.")
        goals.forEach { goal ->
            val percent = (goal.savedMinor * 100 / goal.targetMinor.coerceAtLeast(1)).toInt().coerceIn(0, 100)
            val card = card(rows)
            card.addView(rowText(goal.name, "$percent%", false))
            card.addView(text("${money(goal.savedMinor)} saved of ${money(goal.targetMinor)}", 12, R.color.text_muted).apply { setPadding(0, dp(7), 0, dp(5)) })
            card.addView(ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = percent; progressTintList = ContextCompat.getColorStateList(requireContext(), R.color.forest_600) })
            card.addView(text("Add contribution", 12, R.color.forest_700).apply { setPadding(0, dp(8), 0, 0); setOnClickListener { showAmountDialog("Add to ${goal.name}") { amount -> finance.addToGoal(goal.id, amount) } } })
            card.setOnLongClickListener { confirm("Archive ${goal.name}?", "This goal will be hidden from your active goals.") { finance.archiveGoal(goal) }; true }
        }
    }

    private fun renderRecurring(rows: LinearLayout) {
        val items = finance.recurring.value.orEmpty()
        val active = items.filter { it.active }
        binding.financeSummaryLabel.text = "RECURRING ITEMS"
        binding.financeSummaryAmount.text = "${active.size} active"
        binding.financeSummaryNote.text = "Upcoming regular bills and income"
        binding.financeAddButton.visibility = View.VISIBLE
        binding.financeAddButton.text = "＋  Add recurring item"
        section(rows, "Scheduled")
        if (items.isEmpty()) empty(rows, "No recurring items", "Add rent, subscriptions, salary or another regular item.")
        items.sortedBy { it.nextDueAtMillis }.forEach { item ->
            val card = card(rows)
            card.addView(rowText(item.title, money(item.amountMinor), item.type == "INCOME"))
            card.addView(text("${item.frequency.lowercase().replaceFirstChar(Char::uppercase)}  ·  Next ${date(item.nextDueAtMillis)}  ·  ${if (item.active) "Active" else "Paused"}", 11, R.color.text_muted).apply { setPadding(0, dp(5), 0, 0) })
            card.setOnClickListener { confirm(if (item.active) "Pause ${item.title}?" else "Resume ${item.title}?", "Recurring entries are reminders; they do not automatically create transactions.") { finance.setRecurringActive(item, !item.active) } }
        }
    }

    private fun renderNotifications(rows: LinearLayout) {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<Pair<String, String>>()
        finance.budgets.value.orEmpty().filter { it.month == finance.currentMonth() }.forEach { budget ->
            val amount = finance.transactions.value.orEmpty().filter { it.type == "EXPENSE" && it.categoryId == budget.categoryId && isCurrentMonth(it.occurredAtMillis) }.sumOf { it.amountMinor }
            if (amount >= budget.amountLimitMinor * 8 / 10) {
                val name = finance.categories.value.orEmpty().firstOrNull { it.id == budget.categoryId }?.name ?: "Category"
                alerts += "$name budget" to if (amount >= budget.amountLimitMinor) "You have reached or exceeded your monthly ${name.lowercase()} budget." else "You have used ${amount * 100 / budget.amountLimitMinor.coerceAtLeast(1)}% of your ${name.lowercase()} budget."
            }
        }
        finance.recurring.value.orEmpty().filter { it.active && it.nextDueAtMillis in now..(now + 3 * 24 * 60 * 60 * 1000L) }.forEach {
            alerts += "Coming up: ${it.title}" to "${money(it.amountMinor)} is scheduled ${date(it.nextDueAtMillis)}."
        }
        binding.financeSummaryLabel.text = "PERSONAL REMINDERS"
        binding.financeSummaryAmount.text = alerts.size.toString()
        binding.financeSummaryNote.text = "Based on your budgets and recurring items"
        binding.financeAddButton.visibility = View.GONE
        section(rows, "Recent reminders")
        if (alerts.isEmpty()) empty(rows, "You’re all caught up", "Budget reminders appear when spending reaches 80% of a limit.")
        alerts.forEach { (title, message) -> card(rows).apply { addView(text(title, 15, R.color.text_primary, true)); addView(text(message, 12, R.color.text_muted).apply { setPadding(0, dp(6), 0, 0) }) } }
    }

    private fun renderProfile(rows: LinearLayout) {
        val user = main.currentUser
        binding.financeSummaryLabel.text = "PESATRACK ACCOUNT"
        binding.financeSummaryAmount.text = user?.displayName?.ifBlank { user.email.substringBefore('@') } ?: "PesaTrack member"
        binding.financeSummaryNote.text = user?.email.orEmpty()
        binding.financeAddButton.visibility = View.GONE
        section(rows, "Preferences")
        card(rows).apply {
            addView(rowText("Edit profile", "Update name", false).apply {
                setOnClickListener { showProfileDialog() }
            })
        }
        card(rows).apply {
            addView(rowText("Currency", "Kenyan shilling", false))
            addView(text("Amounts are stored in Kenyan shillings (KES).", 12, R.color.text_muted).apply { setPadding(0, dp(5), 0, 0) })
        }
        card(rows).apply {
            addView(rowText("Account security", "Firebase", false))
            addView(text("Your data is private to your signed-in account.", 12, R.color.text_muted).apply { setPadding(0, dp(5), 0, 0) })
        }
        Button(requireContext()).apply {
            text = "Sign out"; isAllCaps = false; setTextColor(ContextCompat.getColor(requireContext(), R.color.coral)); setBackgroundResource(R.drawable.bg_secondary_button)
            setOnClickListener { confirm("Sign out?", "You can sign back in at any time.") { main.signOut() } }
            rows.addView(this, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        }
    }

    private fun handleAdd() {
        when (screenId) {
            R.id.transactionsFragment, R.id.transactionFormFragment -> showTransactionDialog()
            R.id.budgetsFragment -> showBudgetDialog()
            R.id.categoriesFragment -> showCategoryDialog()
            R.id.accountsFragment -> showAccountDialog()
            R.id.goalsFragment -> showGoalDialog()
            R.id.recurringFragment -> showRecurringDialog()
        }
    }

    private fun showTransactionActions(item: FinanceTransaction) {
        AlertDialog.Builder(requireContext()).setTitle(item.title)
            .setItems(arrayOf("Edit transaction", "Delete transaction")) { _, choice ->
                if (choice == 0) showTransactionDialog(item) else confirm("Delete transaction?", "This will remove it from your totals and reports.") { finance.deleteTransaction(item.id) }
            }.show()
    }

    private fun showTransactionDialog(existing: FinanceTransaction? = null) {
        val title = input("Title, for example groceries")
        val amount = input("Amount in KSh").apply { inputType = 8194; if (existing != null) setText(decimal(existing.amountMinor)) }
        val type = spinner(listOf("Expense", "Income"), if (existing?.type == "INCOME") 1 else 0)
        val categorySpinner = Spinner(requireContext())
        val accountSpinner = Spinner(requireContext())
        val dateButton = Button(requireContext()).apply { isAllCaps = false }
        var occurredAt = existing?.occurredAtMillis ?: System.currentTimeMillis()
        fun updateCategoryChoices() {
            val typeCode = if (type.selectedItemPosition == 1) "INCOME" else "EXPENSE"
            val choices = activeCategories.filter { it.type == typeCode }
            categorySpinner.adapter = adapter(choices.map { it.name })
            val selected = choices.indexOfFirst { it.id == existing?.categoryId }.takeIf { it >= 0 } ?: 0
            if (choices.isNotEmpty()) categorySpinner.setSelection(selected)
        }
        type.onItemSelectedListener = simpleSelection { updateCategoryChoices() }
        updateCategoryChoices()
        val accounts = activeAccounts
        accountSpinner.adapter = adapter(accounts.map { it.name })
        accountSpinner.setSelection(accounts.indexOfFirst { it.id == existing?.accountId }.coerceAtLeast(0))
        fun showDate() {
            val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
            DatePickerDialog(requireContext(), { _, year, month, day ->
                cal.set(year, month, day); occurredAt = cal.timeInMillis
                dateButton.text = "Date: ${date(occurredAt)}"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        dateButton.setOnClickListener { showDate() }; dateButton.text = "Date: ${date(occurredAt)}"
        val notes = input("Notes (optional)").apply { setSingleLine(false); minLines = 2; maxLines = 3; if (existing != null) setText(existing.notes) }
        val content = form(title, amount, type, categorySpinner, accountSpinner, dateButton, notes)
        AlertDialog.Builder(requireContext()).setTitle(if (existing == null) "Add transaction" else "Edit transaction")
            .setView(content).setNegativeButton("Cancel", null).setPositiveButton("Save", null)
            .create().also { dialog ->
                activeDialog = dialog
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val amountMinor = parseAmount(amount.text.toString())
                        val selectedType = if (type.selectedItemPosition == 1) "INCOME" else "EXPENSE"
                        val selectedCategories = activeCategories.filter { it.type == selectedType }
                        val selectedAccount = accounts.getOrNull(accountSpinner.selectedItemPosition)
                        if (title.text.toString().isBlank() || amountMinor == null || amountMinor <= 0L || selectedCategories.isEmpty() || selectedAccount == null) {
                            amount.error = if (amountMinor == null || amountMinor <= 0) "Enter an amount greater than zero" else null
                            if (title.text.isBlank()) title.error = "Enter a title"
                            if (selectedCategories.isEmpty()) toast("Add a category for this transaction first.")
                            if (selectedAccount == null) toast("No active account is available.")
                            return@setOnClickListener
                        }
                        finance.saveTransaction(FinanceTransaction(
                            id = existing?.id.orEmpty(), title = title.text.toString().trim(), type = selectedType,
                            amountMinor = amountMinor, categoryId = selectedCategories[categorySpinner.selectedItemPosition].id,
                            accountId = selectedAccount.id, occurredAtMillis = occurredAt, notes = notes.text.toString(),
                        ))
                    }
                }
                dialog.show()
            }
    }

    private fun showBudgetDialog() {
        val categories = activeCategories.filter { it.type == "EXPENSE" }
        if (categories.isEmpty()) { toast("Add an expense category first."); return }
        val category = spinner(categories.map { it.name })
        val amount = input("Monthly limit in KSh").apply { inputType = 8194 }
        askForm("Set monthly budget", form(category, amount)) {
            val limit = parseAmount(amount.text.toString())
            if (limit == null || limit <= 0L) { amount.error = "Enter a positive limit"; false }
            else { finance.saveBudget(MonthlyBudget(month = finance.currentMonth(), categoryId = categories[category.selectedItemPosition].id, amountLimitMinor = limit)); true }
        }
    }

    private fun showCategoryDialog() {
        val name = input("Category name")
        val type = spinner(listOf("Expense", "Income"))
        askForm("Add category", form(name, type)) {
            if (name.text.isBlank()) { name.error = "Enter a category name"; false }
            else { finance.saveCategory(FinanceCategory(name = name.text.toString(), type = if (type.selectedItemPosition == 1) "INCOME" else "EXPENSE", colorHex = "#2F9B72", sortOrder = activeCategories.size)); true }
        }
    }

    private fun showAccountDialog() {
        val name = input("Account name")
        val type = spinner(listOf("Cash", "Bank", "M-Pesa", "Other"))
        val opening = input("Opening balance in KSh").apply { inputType = 8194; setText("0") }
        askForm("Add account", form(name, type, opening)) {
            val balance = parseAmount(opening.text.toString())
            if (name.text.isBlank() || balance == null || balance < 0L) { name.error = if (name.text.isBlank()) "Enter a name" else null; opening.error = if (balance == null || balance < 0) "Enter a valid balance" else null; false }
            else { finance.saveAccount(FinanceAccount(name = name.text.toString(), type = listOf("CASH", "BANK", "MPESA", "OTHER")[type.selectedItemPosition], openingBalanceMinor = balance)); true }
        }
    }

    private fun showGoalDialog() {
        val name = input("Goal name")
        val target = input("Target amount in KSh").apply { inputType = 8194 }
        val dateButton = Button(requireContext()).apply { text = "Choose target date"; isAllCaps = false }
        var dueAt = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000
        dateButton.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
            DatePickerDialog(requireContext(), { _, y, m, d -> cal.set(y, m, d); dueAt = cal.timeInMillis; dateButton.text = "Target: ${date(dueAt)}" }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        askForm("Create savings goal", form(name, target, dateButton)) {
            val amount = parseAmount(target.text.toString())
            if (name.text.isBlank() || amount == null || amount <= 0) { name.error = if (name.text.isBlank()) "Enter a goal name" else null; target.error = if (amount == null || amount <= 0) "Enter a positive target" else null; false }
            else { finance.saveGoal(SavingsGoal(name = name.text.toString(), targetMinor = amount, targetDateMillis = dueAt)); true }
        }
    }

    private fun showRecurringDialog() {
        val title = input("Name, for example rent")
        val amount = input("Amount in KSh").apply { inputType = 8194 }
        val type = spinner(listOf("Expense", "Income"))
        val frequency = spinner(listOf("Monthly", "Weekly", "Yearly"))
        var categories = activeCategories.filter { it.type == "EXPENSE" }
        val category = spinner(categories.map { it.name })
        type.onItemSelectedListener = simpleSelection {
            categories = activeCategories.filter { it.type == if (type.selectedItemPosition == 1) "INCOME" else "EXPENSE" }
            category.adapter = adapter(categories.map { it.name }.ifEmpty { listOf("No categories available") })
            category.isEnabled = categories.isNotEmpty()
        }
        val accounts = activeAccounts
        val account = spinner(accounts.map { it.name })
        askForm("Add recurring item", form(title, amount, type, frequency, category, account)) {
            val minor = parseAmount(amount.text.toString())
            if (title.text.isBlank() || minor == null || minor <= 0 || categories.isEmpty() || accounts.isEmpty()) {
                if (title.text.isBlank()) title.error = "Enter a title"
                if (minor == null || minor <= 0) amount.error = "Enter a positive amount"
                if (categories.isEmpty() || accounts.isEmpty()) toast("Create a category and account first.")
                false
            } else {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                val selectedType = if (type.selectedItemPosition == 1) "INCOME" else "EXPENSE"
                finance.saveRecurring(RecurringItem(
                    title = title.text.toString(), type = selectedType, amountMinor = minor,
                    categoryId = categories[category.selectedItemPosition].id,
                    accountId = accounts[account.selectedItemPosition].id,
                    frequency = listOf("MONTHLY", "WEEKLY", "YEARLY")[frequency.selectedItemPosition],
                    nextDueAtMillis = cal.timeInMillis,
                )); true
            }
        }
    }

    private fun showAmountDialog(title: String, submit: (Long) -> Unit) {
        val amount = input("Amount in KSh").apply { inputType = 8194 }
        askForm(title, form(amount)) {
            val minor = parseAmount(amount.text.toString())
            if (minor == null || minor <= 0) { amount.error = "Enter an amount greater than zero"; false }
            else { submit(minor); true }
        }
    }

    private fun showProfileDialog() {
        val current = main.currentUser?.displayName.orEmpty()
        val name = input("Full name").apply { setText(current) }
        askForm("Edit profile", form(name)) {
            val value = name.text.toString().trim()
            if (value.length !in 2..40) { name.error = "Enter a name using 2–40 characters"; false }
            else { finance.updateDisplayName(value); true }
        }
    }

    private fun askForm(title: String, content: View, validateAndSubmit: () -> Boolean) {
        val dialog = AlertDialog.Builder(requireContext()).setTitle(title).setView(content).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        activeDialog = dialog
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { validateAndSubmit() } }
        dialog.show()
    }

    private fun form(vararg views: View): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(10), dp(22), dp(6))
        views.forEach { view -> addView(view, LinearLayout.LayoutParams(-1, if (view is EditText) dp(52) else dp(48)).apply { bottomMargin = dp(9) }) }
    }

    private fun input(hintText: String) = EditText(requireContext()).apply {
        hint = hintText; textSize = 15f; setSingleLine(true); setPadding(dp(12), dp(8), dp(12), dp(8))
        setBackgroundResource(R.drawable.bg_input)
    }

    private fun spinner(items: List<String>, selected: Int = 0) = Spinner(requireContext()).apply {
        adapter = adapter(items.ifEmpty { listOf("No options available") })
        if (items.isNotEmpty()) setSelection(selected.coerceIn(items.indices))
        isEnabled = items.isNotEmpty()
    }

    private fun adapter(items: List<String>) = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)

    private fun confirm(title: String, message: String, action: () -> Unit) {
        activeDialog = AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> action() }.show()
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(text(title, 17, R.color.text_primary, true).apply { setPadding(0, dp(12), 0, dp(8)) })
    }

    private fun empty(parent: LinearLayout, title: String, body: String) {
        card(parent).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 15, R.color.text_primary, true))
            addView(text(body, 12, R.color.text_muted).apply { setPadding(0, dp(5), 0, 0) })
        }
    }

    private fun card(parent: LinearLayout) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15)); setBackgroundResource(R.drawable.bg_card)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun rowText(left: String, right: String, positive: Boolean): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        addView(text(left, 14, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f))
        addView(text(right, 14, if (positive) R.color.forest_700 else R.color.text_primary, true).apply { gravity = android.view.Gravity.END })
    }

    private fun text(value: String, sp: Int, color: Int, bold: Boolean = false) = TextView(requireContext()).apply {
        text = value; textSize = sp.toFloat(); setTextColor(ContextCompat.getColor(requireContext(), color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun money(minor: Long): String = "KSh ${NumberFormat.getNumberInstance(Locale.forLanguageTag("en-KE")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(minor / 100.0)}"
    private fun decimal(minor: Long): String = "%.2f".format(Locale.US, minor / 100.0)
    private fun parseAmount(value: String): Long? {
        val minor = value.trim().replace(",", "").toBigDecimalOrNull()?.movePointRight(2)
            ?.setScale(0, java.math.RoundingMode.HALF_UP) ?: return null
        return try { minor.longValueExact() } catch (_: ArithmeticException) { null }
    }
    private fun date(millis: Long) = if (millis <= 0L) "No date" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    private fun isCurrentMonth(millis: Long): Boolean = Calendar.getInstance().let { now ->
        val value = Calendar.getInstance().apply { timeInMillis = millis }
        now.get(Calendar.YEAR) == value.get(Calendar.YEAR) && now.get(Calendar.MONTH) == value.get(Calendar.MONTH)
    }

    private fun simpleSelection(onChanged: () -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = onChanged()
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
