package com.stepwise.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * The one text input every form reuses (DEVELOPMENT_PROTOCOL.md rule 20) — extracted at DEV-016
 * once a second consumer (the Goal form) needed one, the same "extract once a second consumer
 * needs it" pattern `TaskAccess`/`HabitAccess` already established in `:domain`.
 * `:feature:onboarding-auth`'s Sign In screen (DEV-015) fell back to a bare `OutlinedTextField`
 * for lack of this component existing yet — that gap is closed here, not left for a third
 * consumer to rediscover.
 */
@Composable
fun StepwiseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
