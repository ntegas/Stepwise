package com.stepwise.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Determinate progress display — the visual read of a Goal/Task's computed
 * completion, never itself a place progress is stored (`/DATA_MODEL.md`'s
 * "Goal progress is a view, not a column" rule applies equally on the UI side:
 * this composable only renders a `progress` value it's given).
 */
@Composable
fun StepwiseLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().withOptionalDescription(contentDescription),
    )
}

@Composable
fun StepwiseCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    CircularProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.withOptionalDescription(contentDescription),
    )
}

private fun Modifier.withOptionalDescription(description: String?): Modifier =
    if (description != null) semantics { contentDescription = description } else this
