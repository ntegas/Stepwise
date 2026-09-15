package com.stepwise.core.designsystem.component

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The one top bar every screen uses (DEVELOPMENT_PROTOCOL.md rule 20) — a
 * screen never assembles its own `TopAppBar` with ad hoc styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepwiseTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    navigationIconContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = navigationIcon,
                        // Every icon-only control gets a real content description
                        // (DESIGN_SYSTEM.md accessibility requirement) — never null
                        // when the icon is the only affordance for the action.
                        contentDescription = navigationIconContentDescription,
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
