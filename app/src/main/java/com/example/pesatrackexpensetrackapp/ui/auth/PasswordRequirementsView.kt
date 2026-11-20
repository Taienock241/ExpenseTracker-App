package com.example.pesatrackexpensetrackapp.ui.auth

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.pesatrackexpensetrackapp.R

/**
 * Live checklist of password rules. The rules here mirror AuthViewModel.isValidPassword
 * (8+ characters, a letter and a number); keep the two in sync if the policy changes.
 */
class PasswordRequirementsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private class Rule(val label: Int, val isMet: (String) -> Boolean)

    private val rules = listOf(
        Rule(R.string.req_min_length) { it.length >= MIN_LENGTH },
        Rule(R.string.req_letter) { password -> password.any { it.isLetter() } },
        Rule(R.string.req_number) { password -> password.any { it.isDigit() } },
    )

    private val rows = rules.map { rule ->
        TextView(context).apply {
            setText(rule.label)
            textSize = 12f
            compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
            val pad = (3 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
        }
    }

    init {
        orientation = VERTICAL
        rows.forEach { addView(it) }
        update("")
    }

    /** Refreshes every row for [password]; returns true when all rules are met. */
    fun update(password: String): Boolean {
        var allMet = true
        rules.forEachIndexed { index, rule ->
            val met = rule.isMet(password)
            allMet = allMet && met
            val row = rows[index]
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (met) R.drawable.ic_req_met else R.drawable.ic_req_unmet, 0, 0, 0,
            )
            row.setTextColor(ContextCompat.getColor(context, if (met) R.color.forest_700 else R.color.text_muted))
            row.contentDescription = context.getString(
                if (met) R.string.requirement_met else R.string.requirement_unmet,
                context.getString(rule.label),
            )
        }
        return allMet
    }

    private companion object {
        const val MIN_LENGTH = 8
    }
}
