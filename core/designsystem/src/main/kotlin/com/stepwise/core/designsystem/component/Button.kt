package com.stepwise.core.designsystem.component

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Minimum touch target per DESIGN_SYSTEM.md's accessibility requirement (touch target sizes). */
private val MinTouchTarget = 48.dp

@Composable
fun StepwiseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinTouchTarget),
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Composable
fun StepwiseOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinTouchTarget),
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Composable
fun StepwiseTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinTouchTarget),
        enabled = enabled,
    ) {
        Text(text)
    }
}
