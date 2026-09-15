package com.stepwise.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Generic chrome only — the actual destination list (Today/Goals/Plan/Progress,
 * concept §3) is owned by the navigation layer (DEVELOPMENT_PROTOCOL.md rule 21),
 * not hardcoded into the design system.
 */
data class StepwiseNavigationItem(
    val label: String,
    val icon: ImageVector,
    val iconContentDescription: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun StepwiseBottomNavigation(
    items: List<StepwiseNavigationItem>,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = { Icon(item.icon, contentDescription = item.iconContentDescription) },
                label = { Text(item.label) },
            )
        }
    }
}
