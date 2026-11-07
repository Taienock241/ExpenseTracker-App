package com.example.pesatrackexpensetrackapp.data.model

data class FinanceTransaction(
    val id: String = "",
    val title: String = "",
    val type: String = "EXPENSE",
    val amountMinor: Long = 0L,
    val categoryId: String = "",
    val accountId: String = "",
    val occurredAtMillis: Long = 0L,
    val notes: String = "",
)

data class FinanceCategory(
    val id: String = "",
    val name: String = "",
    val type: String = "EXPENSE",
    val iconKey: String = "more",
    val colorHex: String = "#828282",
    val builtIn: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

data class FinanceAccount(
    val id: String = "",
    val name: String = "",
    val type: String = "CASH",
    val openingBalanceMinor: Long = 0L,
    val archived: Boolean = false,
)

data class MonthlyBudget(
    val id: String = "",
    val month: String = "",
    val categoryId: String = "",
    val amountLimitMinor: Long = 0L,
)

data class SavingsGoal(
    val id: String = "",
    val name: String = "",
    val targetMinor: Long = 0L,
    val savedMinor: Long = 0L,
    val targetDateMillis: Long = 0L,
    val archived: Boolean = false,
)

data class RecurringItem(
    val id: String = "",
    val title: String = "",
    val type: String = "EXPENSE",
    val amountMinor: Long = 0L,
    val categoryId: String = "",
    val accountId: String = "",
    val frequency: String = "MONTHLY",
    val nextDueAtMillis: Long = 0L,
    val active: Boolean = true,
)
