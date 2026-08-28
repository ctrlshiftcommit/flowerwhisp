package com.flowerwhisp.mobile.accessibility

import android.text.InputType

data class FieldMetadata(
    val editable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val password: Boolean,
    val inputType: Int,
    val className: String?,
    val viewIdResourceName: String?,
    val hintText: String?,
)

sealed interface FieldPolicyDecision {
    data object Supported : FieldPolicyDecision
    data class Rejected(val sensitive: Boolean, val reason: String) : FieldPolicyDecision
}

object SensitiveFieldPolicy {
    private val stronglySensitiveTerms = listOf(
        "password",
        "passcode",
        "pin",
        "otp",
        "one time",
        "one-time",
        "verification code",
        "security code",
        "cvv",
        "cvc",
        "credit card",
        "debit card",
        "card number",
        "social security",
        "ssn",
    )

    fun evaluate(metadata: FieldMetadata): FieldPolicyDecision {
        if (!metadata.visible || !metadata.enabled || !metadata.editable) {
            return FieldPolicyDecision.Rejected(false, "No supported text field is focused")
        }

        val category = metadata.inputType and InputType.TYPE_MASK_CLASS
        val variation = metadata.inputType and InputType.TYPE_MASK_VARIATION
        val passwordVariation = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val numericOnly = category == InputType.TYPE_CLASS_NUMBER ||
            category == InputType.TYPE_CLASS_PHONE
        val searchableMetadata = listOf(
            metadata.className,
            metadata.viewIdResourceName,
            metadata.hintText,
        ).joinToString(" ").lowercase()
        val stronglySensitive = stronglySensitiveTerms.any(searchableMetadata::contains)

        return when {
            metadata.password || passwordVariation -> FieldPolicyDecision.Rejected(
                true,
                "FlowerWhisp does not record or insert into password fields",
            )
            numericOnly -> FieldPolicyDecision.Rejected(
                true,
                "FlowerWhisp does not record or insert into PIN, phone, or numeric-only fields",
            )
            stronglySensitive -> FieldPolicyDecision.Rejected(
                true,
                "FlowerWhisp does not record or insert into strongly sensitive fields",
            )
            else -> FieldPolicyDecision.Supported
        }
    }
}
