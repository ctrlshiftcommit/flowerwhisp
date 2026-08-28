package com.flowerwhisp.mobile.accessibility

data class FocusedField(
    val available: Boolean = false,
    val sensitive: Boolean = false,
    val packageName: String = "",
    val reason: String = "No supported text field is focused",
    val token: TargetToken? = null,
)
