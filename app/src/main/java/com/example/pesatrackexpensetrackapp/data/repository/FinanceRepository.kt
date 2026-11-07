package com.example.pesatrackexpensetrackapp.data.repository

import com.example.pesatrackexpensetrackapp.data.model.FinanceAccount
import com.example.pesatrackexpensetrackapp.data.model.FinanceCategory
import com.example.pesatrackexpensetrackapp.data.model.FinanceTransaction
import com.example.pesatrackexpensetrackapp.data.model.MonthlyBudget
import com.example.pesatrackexpensetrackapp.data.model.RecurringItem
import com.example.pesatrackexpensetrackapp.data.model.SavingsGoal
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

sealed interface FinanceResult<out T> {
    data class Success<T>(val value: T) : FinanceResult<T>
    data class Failure(val reason: RepositoryFailure) : FinanceResult<Nothing>
}

interface FinanceRepository {
    fun watchTransactions(uid: String, callback: (FinanceResult<List<FinanceTransaction>>) -> Unit): ListenerRegistration
    fun watchCategories(uid: String, callback: (FinanceResult<List<FinanceCategory>>) -> Unit): ListenerRegistration
    fun watchAccounts(uid: String, callback: (FinanceResult<List<FinanceAccount>>) -> Unit): ListenerRegistration
    fun watchBudgets(uid: String, callback: (FinanceResult<List<MonthlyBudget>>) -> Unit): ListenerRegistration
    fun watchGoals(uid: String, callback: (FinanceResult<List<SavingsGoal>>) -> Unit): ListenerRegistration
    fun watchRecurring(uid: String, callback: (FinanceResult<List<RecurringItem>>) -> Unit): ListenerRegistration
    fun saveTransaction(uid: String, value: FinanceTransaction, callback: (FinanceResult<String>) -> Unit)
    fun deleteTransaction(uid: String, id: String, callback: (FinanceResult<Unit>) -> Unit)
    fun saveCategory(uid: String, value: FinanceCategory, callback: (FinanceResult<String>) -> Unit)
    fun archiveCategory(uid: String, value: FinanceCategory, callback: (FinanceResult<Unit>) -> Unit)
    fun saveAccount(uid: String, value: FinanceAccount, callback: (FinanceResult<String>) -> Unit)
    fun archiveAccount(uid: String, value: FinanceAccount, callback: (FinanceResult<Unit>) -> Unit)
    fun saveBudget(uid: String, value: MonthlyBudget, callback: (FinanceResult<String>) -> Unit)
    fun saveGoal(uid: String, value: SavingsGoal, callback: (FinanceResult<String>) -> Unit)
    fun addToGoal(uid: String, goalId: String, amountMinor: Long, callback: (FinanceResult<Unit>) -> Unit)
    fun archiveGoal(uid: String, value: SavingsGoal, callback: (FinanceResult<Unit>) -> Unit)
    fun saveRecurring(uid: String, value: RecurringItem, callback: (FinanceResult<String>) -> Unit)
    fun setRecurringActive(uid: String, value: RecurringItem, active: Boolean, callback: (FinanceResult<Unit>) -> Unit)
    fun updateDisplayName(uid: String, name: String, callback: (FinanceResult<Unit>) -> Unit)
}

class FirestoreFinanceRepository(
    private val firestore: FirebaseFirestore,
) : FinanceRepository {
    private fun user(uid: String) = firestore.collection("users").document(uid)

    private fun <T> watch(
        uid: String,
        path: String,
        orderField: String? = null,
        converter: (String, Map<String, Any>) -> T,
        callback: (FinanceResult<List<T>>) -> Unit,
    ): ListenerRegistration {
        var query: Query = user(uid).collection(path)
        if (orderField != null) query = query.orderBy(orderField, Query.Direction.DESCENDING)
        return query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                callback(FinanceResult.Failure(if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) RepositoryFailure.NETWORK else RepositoryFailure.UNKNOWN))
            } else {
                callback(FinanceResult.Success(snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.data?.let { converter(document.id, it) }
                }))
            }
        }
    }

    override fun watchTransactions(uid: String, callback: (FinanceResult<List<FinanceTransaction>>) -> Unit) =
        watch(uid, "transactions", "occurredAt", ::transactionFrom, callback)

    override fun watchCategories(uid: String, callback: (FinanceResult<List<FinanceCategory>>) -> Unit) =
        watch(uid, "categories", converter = ::categoryFrom, callback = callback)

    override fun watchAccounts(uid: String, callback: (FinanceResult<List<FinanceAccount>>) -> Unit) =
        watch(uid, "accounts", converter = ::accountFrom, callback = callback)

    override fun watchBudgets(uid: String, callback: (FinanceResult<List<MonthlyBudget>>) -> Unit) =
        watch(uid, "budgets", converter = ::budgetFrom, callback = callback)

    override fun watchGoals(uid: String, callback: (FinanceResult<List<SavingsGoal>>) -> Unit) =
        watch(uid, "goals", converter = ::goalFrom, callback = callback)

    override fun watchRecurring(uid: String, callback: (FinanceResult<List<RecurringItem>>) -> Unit) =
        watch(uid, "recurring", converter = ::recurringFrom, callback = callback)

    override fun saveTransaction(uid: String, value: FinanceTransaction, callback: (FinanceResult<String>) -> Unit) {
        val ref = value.id.takeIf(String::isNotBlank)?.let { user(uid).collection("transactions").document(it) }
            ?: user(uid).collection("transactions").document()
        val map = mapOf(
            "title" to value.title.trim(), "type" to value.type, "amountMinor" to value.amountMinor,
            "categoryId" to value.categoryId, "accountId" to value.accountId,
            "occurredAt" to Timestamp(java.util.Date(value.occurredAtMillis)), "notes" to value.notes.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(map, SetOptions.merge()).complete(ref.id, callback)
    }

    override fun deleteTransaction(uid: String, id: String, callback: (FinanceResult<Unit>) -> Unit) =
        user(uid).collection("transactions").document(id).delete().complete(Unit, callback)

    override fun saveCategory(uid: String, value: FinanceCategory, callback: (FinanceResult<String>) -> Unit) {
        val ref = value.id.takeIf(String::isNotBlank)?.let { user(uid).collection("categories").document(it) }
            ?: user(uid).collection("categories").document()
        val old = mapOf("createdAt" to Timestamp.now())
        val data = mapOf(
            "name" to value.name.trim(), "type" to value.type, "iconKey" to value.iconKey,
            "colorHex" to value.colorHex, "builtIn" to value.builtIn, "archived" to value.archived,
            "sortOrder" to value.sortOrder,
        )
        ref.set(data + old, SetOptions.merge()).complete(ref.id, callback)
    }

    override fun archiveCategory(uid: String, value: FinanceCategory, callback: (FinanceResult<Unit>) -> Unit) =
        user(uid).collection("categories").document(value.id).update("archived", true).complete(Unit, callback)

    override fun saveAccount(uid: String, value: FinanceAccount, callback: (FinanceResult<String>) -> Unit) {
        val ref = value.id.takeIf(String::isNotBlank)?.let { user(uid).collection("accounts").document(it) }
            ?: user(uid).collection("accounts").document()
        val data = mapOf(
            "name" to value.name.trim(), "type" to value.type, "openingBalanceMinor" to value.openingBalanceMinor,
            "archived" to value.archived, "createdAt" to Timestamp.now(),
        )
        ref.set(data, SetOptions.merge()).complete(ref.id, callback)
    }

    override fun archiveAccount(uid: String, value: FinanceAccount, callback: (FinanceResult<Unit>) -> Unit) =
        user(uid).collection("accounts").document(value.id).update("archived", true).complete(Unit, callback)

    override fun saveBudget(uid: String, value: MonthlyBudget, callback: (FinanceResult<String>) -> Unit) {
        val id = "${value.month}_${value.categoryId}"
        user(uid).collection("budgets").document(id).set(
            mapOf("month" to value.month, "categoryId" to value.categoryId,
                "amountLimitMinor" to value.amountLimitMinor, "createdAt" to Timestamp.now()),
            SetOptions.merge(),
        ).complete(id, callback)
    }

    override fun saveGoal(uid: String, value: SavingsGoal, callback: (FinanceResult<String>) -> Unit) {
        val ref = value.id.takeIf(String::isNotBlank)?.let { user(uid).collection("goals").document(it) }
            ?: user(uid).collection("goals").document()
        val data = mapOf(
            "name" to value.name.trim(), "targetMinor" to value.targetMinor, "savedMinor" to value.savedMinor,
            "targetDate" to Timestamp(java.util.Date(value.targetDateMillis)), "archived" to value.archived,
            "createdAt" to Timestamp.now(),
        )
        ref.set(data, SetOptions.merge()).complete(ref.id, callback)
    }

    override fun addToGoal(uid: String, goalId: String, amountMinor: Long, callback: (FinanceResult<Unit>) -> Unit) {
        val ref = user(uid).collection("goals").document(goalId)
        firestore.runTransaction { transaction ->
            val current = transaction.get(ref).getLong("savedMinor") ?: 0L
            transaction.update(ref, "savedMinor", current + amountMinor)
        }.complete(Unit, callback)
    }

    override fun archiveGoal(uid: String, value: SavingsGoal, callback: (FinanceResult<Unit>) -> Unit) =
        user(uid).collection("goals").document(value.id).update("archived", true).complete(Unit, callback)

    override fun saveRecurring(uid: String, value: RecurringItem, callback: (FinanceResult<String>) -> Unit) {
        val ref = value.id.takeIf(String::isNotBlank)?.let { user(uid).collection("recurring").document(it) }
            ?: user(uid).collection("recurring").document()
        val data = mapOf(
            "title" to value.title.trim(), "type" to value.type, "amountMinor" to value.amountMinor,
            "categoryId" to value.categoryId, "accountId" to value.accountId, "frequency" to value.frequency,
            "nextDueAt" to Timestamp(java.util.Date(value.nextDueAtMillis)), "active" to value.active,
            "createdAt" to Timestamp.now(),
        )
        ref.set(data, SetOptions.merge()).complete(ref.id, callback)
    }

    override fun setRecurringActive(uid: String, value: RecurringItem, active: Boolean, callback: (FinanceResult<Unit>) -> Unit) =
        user(uid).collection("recurring").document(value.id).update("active", active).complete(Unit, callback)

    override fun updateDisplayName(uid: String, name: String, callback: (FinanceResult<Unit>) -> Unit) =
        user(uid).update("displayName", name.trim()).complete(Unit, callback)

    private fun transactionFrom(id: String, data: Map<String, Any>) = FinanceTransaction(
        id, data.string("title"), data.string("type", "EXPENSE"), data.long("amountMinor"),
        data.string("categoryId"), data.string("accountId"), data.millis("occurredAt"), data.string("notes"),
    )
    private fun categoryFrom(id: String, data: Map<String, Any>) = FinanceCategory(
        id, data.string("name"), data.string("type", "EXPENSE"), data.string("iconKey", "more"),
        data.string("colorHex", "#828282"), data.bool("builtIn"), data.bool("archived"), data.int("sortOrder"),
    )
    private fun accountFrom(id: String, data: Map<String, Any>) = FinanceAccount(
        id, data.string("name"), data.string("type", "CASH"), data.long("openingBalanceMinor"), data.bool("archived"),
    )
    private fun budgetFrom(id: String, data: Map<String, Any>) = MonthlyBudget(
        id, data.string("month"), data.string("categoryId"), data.long("amountLimitMinor"),
    )
    private fun goalFrom(id: String, data: Map<String, Any>) = SavingsGoal(
        id, data.string("name"), data.long("targetMinor"), data.long("savedMinor"), data.millis("targetDate"), data.bool("archived"),
    )
    private fun recurringFrom(id: String, data: Map<String, Any>) = RecurringItem(
        id, data.string("title"), data.string("type", "EXPENSE"), data.long("amountMinor"),
        data.string("categoryId"), data.string("accountId"), data.string("frequency", "MONTHLY"),
        data.millis("nextDueAt"), data.bool("active", true),
    )

    private fun Map<String, Any>.string(key: String, default: String = "") = this[key] as? String ?: default
    private fun Map<String, Any>.long(key: String) = (this[key] as? Number)?.toLong() ?: 0L
    private fun Map<String, Any>.int(key: String) = (this[key] as? Number)?.toInt() ?: 0
    private fun Map<String, Any>.bool(key: String, default: Boolean = false) = this[key] as? Boolean ?: default
    private fun Map<String, Any>.millis(key: String) = (this[key] as? Timestamp)?.toDate()?.time ?: 0L

    private fun <T> com.google.android.gms.tasks.Task<*>.complete(
        successValue: T,
        callback: (FinanceResult<T>) -> Unit,
    ) {
        addOnSuccessListener { callback(FinanceResult.Success(successValue)) }
            .addOnFailureListener { error ->
                callback(FinanceResult.Failure(if (error is com.google.firebase.FirebaseNetworkException) RepositoryFailure.NETWORK else RepositoryFailure.UNKNOWN))
            }
    }
}
