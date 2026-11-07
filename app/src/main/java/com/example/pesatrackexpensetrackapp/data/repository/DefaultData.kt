package com.example.pesatrackexpensetrackapp.data.repository

data class DefaultCategory(
    val id: String,
    val name: String,
    val type: String,
    val iconKey: String,
    val colorHex: String,
    val sortOrder: Int,
)

object DefaultData {
    val categories = listOf(
        DefaultCategory("cat_food", "Food", "EXPENSE", "food", "#45B87B", 0),
        DefaultCategory("cat_transport", "Transport", "EXPENSE", "transport", "#2F80ED", 1),
        DefaultCategory("cat_airtime", "Airtime and Data", "EXPENSE", "phone", "#56CCF2", 2),
        DefaultCategory("cat_rent", "Rent and Housing", "EXPENSE", "home", "#9B51E0", 3),
        DefaultCategory("cat_bills", "Bills and Utilities", "EXPENSE", "receipt", "#F2994A", 4),
        DefaultCategory("cat_shopping", "Shopping", "EXPENSE", "shopping", "#EB5757", 5),
        DefaultCategory("cat_health", "Health", "EXPENSE", "health", "#27AE60", 6),
        DefaultCategory("cat_education", "Education", "EXPENSE", "education", "#2D9CDB", 7),
        DefaultCategory("cat_entertainment", "Entertainment", "EXPENSE", "entertainment", "#BB6BD9", 8),
        DefaultCategory("cat_other_expense", "Other", "EXPENSE", "more", "#828282", 9),
        DefaultCategory("cat_salary", "Salary", "INCOME", "salary", "#27AE60", 0),
        DefaultCategory("cat_allowance", "Allowance", "INCOME", "wallet", "#2F80ED", 1),
        DefaultCategory("cat_business", "Business", "INCOME", "business", "#F2994A", 2),
        DefaultCategory("cat_gift", "Gifts", "INCOME", "gift", "#BB6BD9", 3),
        DefaultCategory("cat_other_income", "Other", "INCOME", "more", "#828282", 4),
    )

    const val CASH_ACCOUNT_ID = "acc_cash"
}
