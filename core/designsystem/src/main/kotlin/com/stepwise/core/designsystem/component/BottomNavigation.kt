package com.stepwise.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
