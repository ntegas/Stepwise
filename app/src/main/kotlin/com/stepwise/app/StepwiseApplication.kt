package com.stepwise.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The Hilt DI graph's root (DEV-015) - every `@Module` in `:app/.../di` attaches here. */
@HiltAndroidApp
class StepwiseApplication : Application()
