package com.stepwise.feature.onboardingauth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_EMAIL = "sign_in_email"

/**
 * A deliberately minimal Sign In/Sign Up screen — `/ARCHITECTURE.md` §5 already names
 * `:feature:onboarding-auth` in the canonical module graph (fixed here at DEV-015 review,
 * architecture reviewer, MEDIUM — an earlier draft built this directly inside `:app` instead,
 * routing around an already-decided module placement). No Google Sign-In (needs Android
 * Credential Manager wiring, out of scope here), no password-reset flow, no onboarding copy
 * beyond the bare form — `/ROADMAP.md` names no dedicated Auth UI task anywhere (confirmed via
 * grep), yet DEV-015's own "First major visual/product checkpoint" needs *some* reachable entry
 * point into Today, since `AuthRepository` (DEV-007) had no caller anywhere in this app before
 * now. A later task should decide where real Auth UI (Google Sign-In, forgot-password, onboarding
 * copy) belongs beyond this minimum, rather than this checkpoint quietly becoming the final word.
 *
 * [SignInUiState.error]/[SignInUiState.signUpNeedsEmailConfirmation] are UI-agnostic signals, not
 * resolved strings — this ViewModel never resolves a string resource itself (no `Context`
 * injected), leaving that to `SignInScreen`'s own `stringResource()` calls, so the state stays
 * trivially unit-testable and the resource lookup stays where Compose already expects it.
 *
 * [email] survives process death via [SavedStateHandle] (DEV-015 review, logic reviewer, LOW —
 * "process recreation" is a named checkpoint criterion this screen didn't actually satisfy).
 * [password] deliberately does not — never persisting a typed password across process death is
 * the ordinary, expected security posture, not an oversight.
 */
@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(SignInUiState(email = savedStateHandle[KEY_EMAIL] ?: ""))
        val state: StateFlow<SignInUiState> = _state.asStateFlow()

        fun onEmailChanged(email: String) {
            savedStateHandle[KEY_EMAIL] = email
            _state.value = _state.value.copy(email = email, error = null, signUpNeedsEmailConfirmation = false)
        }

        fun onPasswordChanged(password: String) {
            _state.value = _state.value.copy(password = password, error = null, signUpNeedsEmailConfirmation = false)
        }

        fun signIn() {
            if (_state.value.isSubmitting) return
            submit { authRepository.signInWithEmail(_state.value.email, _state.value.password) }
        }

        fun signUp() {
            if (_state.value.isSubmitting) return
            submit(afterSuccess = ::checkSignUpCreatedASession) {
                authRepository.signUpWithEmail(_state.value.email, _state.value.password)
            }
        }

        /**
         * A real Supabase project can require email confirmation before a session exists — DEV-015
         * review (user-flow reviewer, HIGH): [AuthRepository.signUpWithEmail] returning `Success`
         * doesn't mean the user is actually signed in, and the earlier draft left the form sitting
         * there with zero acknowledgment either way when confirmation was required.
         */
        private suspend fun checkSignUpCreatedASession() {
            if (authRepository.observeUserId().first() == null) {
                _state.value = _state.value.copy(signUpNeedsEmailConfirmation = true)
            }
        }

        private fun submit(
            afterSuccess: (suspend () -> Unit)? = null,
            action: suspend () -> AppResult<Unit>,
        ) {
            _state.value = _state.value.copy(isSubmitting = true, error = null, signUpNeedsEmailConfirmation = false)
            viewModelScope.launch {
                val result = action()
                when (result) {
                    is AppResult.Success -> {
                        _state.value = _state.value.copy(password = "")
                        afterSuccess?.invoke()
                    }
                    is AppResult.Failure -> {
                        _state.value = _state.value.copy(password = "", error = result.error.toSignInError())
                    }
                }
                _state.value = _state.value.copy(isSubmitting = false)
            }
        }
    }

/**
 * Never the raw [AppError.message] shown to a user (DEV-015 review, security reviewer, MEDIUM) —
 * the underlying mapper (`NetworkErrorMapping`, DEV-007) falls back to a raw exception message
 * for several branches, and `/docs/security/THREAT_MODEL.md`'s own Auth section already tracks
 * "account enumeration via differing sign-up/sign-in error messages" as verification debt. One
 * fixed, generic [SignInError] per [AppError] kind closes that for this, the first call site that
 * ever surfaces one to a user; `SignInScreen` resolves the actual displayed string.
 */
private fun AppError.toSignInError(): SignInError =
    when (this) {
        is AppError.Auth -> SignInError.INVALID_CREDENTIALS
        is AppError.Network -> SignInError.NETWORK
        else -> SignInError.UNKNOWN
    }

enum class SignInError {
    INVALID_CREDENTIALS,
    NETWORK,
    UNKNOWN,
}

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: SignInError? = null,
    val signUpNeedsEmailConfirmation: Boolean = false,
)
