package com.example.pesatrackexpensetrackapp.ui.auth

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.pesatrackexpensetrackapp.R

/**
 * Label + rounded input + inline error. Passwords automatically get a show/hide toggle.
 *
 * Attributes: app:fieldLabel, android:hint, android:inputType, android:imeOptions.
 */
class AuthField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    val editText: EditText
    private val label: TextView
    private val toggle: ImageButton
    private val errorText: TextView
    private val isPassword: Boolean
    private var passwordVisible = false

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_auth_field, this, true)
        label = findViewById(R.id.authFieldLabel)
        editText = findViewById(R.id.authFieldInput)
        toggle = findViewById(R.id.authFieldToggle)
        errorText = findViewById(R.id.authFieldError)

        val a = context.obtainStyledAttributes(attrs, R.styleable.AuthField)
        label.text = a.getString(R.styleable.AuthField_fieldLabel)
        editText.hint = a.getString(R.styleable.AuthField_android_hint)
        val inputType = a.getInt(R.styleable.AuthField_android_inputType, InputType.TYPE_CLASS_TEXT)
        editText.imeOptions = a.getInt(R.styleable.AuthField_android_imeOptions, EditorInfo.IME_ACTION_NEXT)
        a.recycle()

        editText.inputType = inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        isPassword = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)

        // A unique id lets the label point at its input (TalkBack reads "Label, edit box").
        editText.id = View.generateViewId()
        label.labelFor = editText.id
        // Several fields share the same inner ids, so the field saves its own text (see onSaveInstanceState).
        editText.isSaveEnabled = false

        if (isPassword) {
            toggle.isVisible = true
            editText.setPaddingRelative(editText.paddingStart, 0, dp(52), 0)
            applyPasswordVisibility()
            toggle.setOnClickListener {
                passwordVisible = !passwordVisible
                applyPasswordVisibility()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when {
                isPassword -> editText.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ->
                    editText.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
            }
        }

        // Typing clears a previous error so it never lingers while the user corrects it.
        editText.doAfterTextChanged { if (errorText.isVisible) error = null }
    }

    val text: Editable get() = editText.text

    var error: CharSequence?
        get() = errorText.text.takeIf { errorText.isVisible }
        set(value) {
            val hasError = !value.isNullOrEmpty()
            errorText.text = value
            errorText.isVisible = hasError
            editText.isActivated = hasError
        }

    fun addOnTextChanged(action: (Editable?) -> Unit) {
        editText.doAfterTextChanged(action)
    }

    private fun applyPasswordVisibility() {
        val selection = editText.selectionEnd
        editText.transformationMethod = if (passwordVisible) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        if (selection >= 0) editText.setSelection(selection)
        toggle.setImageResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
        toggle.contentDescription = context.getString(
            if (passwordVisible) R.string.hide_password else R.string.show_password,
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(KEY_SUPER, super.onSaveInstanceState())
        putString(KEY_TEXT, editText.text.toString())
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(BundleCompat.getParcelable(state, KEY_SUPER, Parcelable::class.java))
            editText.setText(state.getString(KEY_TEXT))
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private companion object {
        const val KEY_SUPER = "super"
        const val KEY_TEXT = "text"
    }
}
