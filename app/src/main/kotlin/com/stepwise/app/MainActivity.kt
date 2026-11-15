package com.stepwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.stepwise.app.navigation.StepwiseNavHost
import com.stepwise.core.designsystem.theme.StepwiseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StepwiseTheme {
                StepwiseNavHost()
            }
        }
    }
}
