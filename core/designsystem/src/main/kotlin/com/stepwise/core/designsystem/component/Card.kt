package com.stepwise.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stepwise.core.designsystem.theme.StepwiseElevation
import com.stepwise.core.designsystem.theme.StepwiseSpacing

/**
 * The one card container every feature reuses (DEVELOPMENT_PROTOCOL.md rule 20) —
 * `StepwiseGoalCard`/`StepwiseTaskRow`/etc. are built on top of this once the
 * domain models they display exist (DEV-004+), rather than each inventing its own
 * card chrome.
 */
@Composable
fun StepwiseCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = StepwiseElevation.level1),
    ) {
        Column(modifier = Modifier.padding(StepwiseSpacing.medium)) {
            content()
        }
    }
}
