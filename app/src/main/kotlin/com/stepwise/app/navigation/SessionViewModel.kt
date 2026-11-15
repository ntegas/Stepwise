package com.stepwise.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.model.UserId
import com.stepwise.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The one place `:app` reads [AuthRepository.observeUserId] to decide which top-level
 * destination [StepwiseNavHost] shows - never duplicated in
 * [com.stepwise.feature.onboardingauth.SignInViewModel] or `TodayViewModel`, which each only
 * react to signed-in/signed-out locally within their own screen rather than owning cross-screen
 * navigation.
 */
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
    ) : ViewModel() {
        val userId: StateFlow<UserId?> =
            authRepository.observeUserId().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }
