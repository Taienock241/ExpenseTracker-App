package com.example.pesatrackexpensetrackapp.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pesatrackexpensetrackapp.data.model.FinanceAccount
import com.example.pesatrackexpensetrackapp.data.model.FinanceCategory
import com.example.pesatrackexpensetrackapp.data.model.FinanceTransaction
import com.example.pesatrackexpensetrackapp.data.model.MonthlyBudget
import com.example.pesatrackexpensetrackapp.data.model.RecurringItem
import com.example.pesatrackexpensetrackapp.data.model.SavingsGoal
import com.example.pesatrackexpensetrackapp.data.repository.AuthRepository
import com.example.pesatrackexpensetrackapp.data.repository.FinanceRepository
import com.example.pesatrackexpensetrackapp.data.repository.FinanceResult
import com.example.pesatrackexpensetrackapp.data.repository.RepositoryFailure
import com.example.pesatrackexpensetrackapp.data.repository.RepositoryResult
import com.example.pesatrackexpensetrackapp.utils.Event
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class FinanceViewModel(
    private val authRepository: AuthRepository,
    private val repository: FinanceRepository,
) : ViewModel() {
    private val uid = authRepository.currentUser()?.uid
    private val registrations = mutableListOf<ListenerRegistration>()

    private val _transactions = MutableLiveData<List<FinanceTransaction>>(emptyList())
    val transactions: LiveData<List<FinanceTransaction>> = _transactions
    private val _categories = MutableLiveData<List<FinanceCategory>>(emptyList())
    val categories: LiveData<List<FinanceCategory>> = _categories
    private val _accounts = MutableLiveData<List<FinanceAccount>>(emptyList())
    val accounts: LiveData<List<FinanceAccount>> = _accounts
    private val _budgets = MutableLiveData<List<MonthlyBudget>>(emptyList())
    val budgets: LiveData<List<MonthlyBudget>> = _budgets
    private val _goals = MutableLiveData<List<SavingsGoal>>(emptyList())
    val goals: LiveData<List<SavingsGoal>> = _goals
    private val _recurring = MutableLiveData<List<RecurringItem>>(emptyList())
    val recurring: LiveData<List<RecurringItem>> = _recurring

    private val _message = MutableLiveData<Event<String>>()
    val message: LiveData<Event<String>> = _message
    private val _saved = MutableLiveData<Event<Unit>>()
    val saved: LiveData<Event<Unit>> = _saved
    private val _profileUpdated = MutableLiveData<Event<Unit>>()
    val profileUpdated: LiveData<Event<Unit>> = _profileUpdated
    init {
        uid?.let { userId ->
            registrations += repository.watchTransactions(userId) { it.receive(_transactions) }
            registrations += repository.watchCategories(userId) { it.receive(_categories) }
            registrations += repository.watchAccounts(userId) { it.receive(_accounts) }
            registrations += repository.watchBudgets(userId) { it.receive(_budgets) }
            registrations += repository.watchGoals(userId) { it.receive(_goals) }
            registrations += repository.watchRecurring(userId) { it.receive(_recurring) }
        }
    }

    fun saveTransaction(value: FinanceTransaction) = perform("Transaction saved") { repository.saveTransaction(it, value, ::finish) }
    fun deleteTransaction(id: String) = perform("Transaction deleted") { repository.deleteTransaction(it, id, ::finish) }
    fun saveCategory(value: FinanceCategory) = perform("Category saved") { repository.saveCategory(it, value, ::finish) }
    fun archiveCategory(value: FinanceCategory) = perform("Category archived") { repository.archiveCategory(it, value, ::finish) }
    fun saveAccount(value: FinanceAccount) = perform("Account saved") { repository.saveAccount(it, value, ::finish) }
    fun archiveAccount(value: FinanceAccount) = perform("Account archived") { repository.archiveAccount(it, value, ::finish) }
    fun saveBudget(value: MonthlyBudget) = perform("Budget saved") { repository.saveBudget(it, value, ::finish) }
    fun saveGoal(value: SavingsGoal) = perform("Savings goal saved") { repository.saveGoal(it, value, ::finish) }
    fun addToGoal(goalId: String, amountMinor: Long) = perform("Contribution added") { repository.addToGoal(it, goalId, amountMinor, ::finish) }
    fun archiveGoal(value: SavingsGoal) = perform("Savings goal archived") { repository.archiveGoal(it, value, ::finish) }
    fun saveRecurring(value: RecurringItem) = perform("Recurring item saved") { repository.saveRecurring(it, value, ::finish) }
    fun setRecurringActive(value: RecurringItem, active: Boolean) = perform(
        if (active) "Recurring item resumed" else "Recurring item paused",
    ) { repository.setRecurringActive(it, value, active, ::finish) }

    fun updateDisplayName(name: String) {
        val userId = uid
        if (userId == null) { reportError("Please sign in again to continue."); return }
        authRepository.updateDisplayName(name) { authResult ->
            when (authResult) {
                RepositoryResult.Success -> repository.updateDisplayName(userId, name) { result ->
                    when (result) {
                        is FinanceResult.Success -> {
                            _message.value = Event("Profile updated")
                            _saved.value = Event(Unit)
                            _profileUpdated.value = Event(Unit)
                        }
                        is FinanceResult.Failure -> reportError("Your account name changed, but your profile could not sync. Please try again.")
                    }
                }
                is RepositoryResult.Failure -> reportError(
                    if (authResult.reason == RepositoryFailure.NETWORK) "Check your internet connection and try again."
                    else "Could not update your account name. Please try again.",
                )
            }
        }
    }

    fun reportError(message: String) { _message.value = Event(message) }

    private fun perform(successMessage: String, action: (String) -> Unit) {
        val userId = uid
        if (userId == null) {
            reportError("Please sign in again to continue.")
            return
        }
        action(userId)
    }

    private fun <T> finish(result: FinanceResult<T>) {
        when (result) {
            is FinanceResult.Success -> {
                _message.value = Event("Saved successfully")
                _saved.value = Event(Unit)
            }
            is FinanceResult.Failure -> _message.value = Event(
                if (result.reason == RepositoryFailure.NETWORK) "Check your internet connection and try again."
                else "Could not save your changes. Check your Firestore rules and try again.",
            )
        }
    }

    private fun <T> FinanceResult<List<T>>.receive(target: MutableLiveData<List<T>>) {
        when (this) {
            is FinanceResult.Success -> target.value = value
            is FinanceResult.Failure -> _message.value = Event(
                if (reason == RepositoryFailure.NETWORK) "Offline. Showing the latest available data."
                else "Could not load your data. Check your Firestore rules.",
            )
        }
    }

    fun currentMonth(): String = Calendar.getInstance().let {
        "%04d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
    }

    override fun onCleared() {
        registrations.forEach(ListenerRegistration::remove)
        registrations.clear()
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val financeRepository: FinanceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                return FinanceViewModel(authRepository, financeRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
