package com.stepwise.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.stepwise.core.designsystem.theme.StepwiseIconSize
import com.stepwise.core.designsystem.theme.StepwiseSpacing

/**
 * The three states every list/screen needs (DESIGN_SYSTEM.md's named component
 * list) — one shared implementation each, so "no data yet," "loading," and
 * "something went wrong" always look and behave the same way across features.
 *
 * All user-facing text is a caller-supplied parameter, never a string literal
 * here — this module has no string-resource/localization layer of its own
 * (DEVELOPMENT_PROTOCOL.md rule 25), the feature module calling it does.
 */
@Composable
fun StepwiseEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(StepwiseSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(StepwiseIconSize.extraLarge)
                        .padding(bottom = StepwiseSpacing.medium),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.padding(top = StepwiseSpacing.medium))
            action()
        }
    }
}

@Composable
fun StepwiseLoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(StepwiseSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(bottom = StepwiseSpacing.medium))
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StepwiseErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(StepwiseSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier =
                Modifier
                    .size(StepwiseIconSize.extraLarge)
                    .padding(bottom = StepwiseSpacing.medium),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onRetry != null && retryLabel != null) {
            Spacer(Modifier.padding(top = StepwiseSpacing.medium))
            StepwiseTextButton(text = retryLabel, onClick = onRetry)
        }
    }
}
