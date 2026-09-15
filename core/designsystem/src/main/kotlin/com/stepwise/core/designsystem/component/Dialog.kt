package com.stepwise.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one dialog shell every confirmation/destructive-action flow uses
 * (DEVELOPMENT_PROTOCOL.md rule 20). All text is caller-supplied — no
 * string literal here (rule 25).
 */
@Composable
fun StepwiseDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton =
            dismissLabel?.let {
                { TextButton(onClick = onDismiss) { Text(it) } }
            },
    )
}
