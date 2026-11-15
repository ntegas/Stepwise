package com.stepwise.feature.onboardingauth

import androidx.lifecycle.SavedStateHandle
import com.stepwise.core.common.AppError
import com.stepwise.core.common.AppResult
import com.stepwise.core.model.UserId
import com.stepwise.domain.fake.FakeAuthRepository
import com.stepwise.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class FailingAuthRepository(
    private val message: String,
) : AuthRepository by FakeAuthRepository() {
    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AppResult<Unit> = AppResult.Failure(AppError.Auth(message))
}

/** A real backend returning `Success` without ever setting a session (email confirmation pending). */
private class ConfirmationPendingAuthRepository : AuthRepository by FakeAuthRepository()

class SignInViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(authRepository: AuthRepository) = SignInViewModel(authRepository, SavedStateHandle())

    @Test
    fun `typing email and password updates state and clears any previous error`() =
        runTest {
            val viewModel = viewModel(FakeAuthRepository())

            viewModel.onEmailChanged("a@b.com")
            viewModel.onPasswordChanged("secret")

            assertEquals("a@b.com", viewModel.state.value.email)
            assertEquals("secret", viewModel.state.value.password)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `a successful sign-in clears isSubmitting, has no error, and clears the password field`() =
        runTest {
            val viewModel = viewModel(FakeAuthRepository())
            viewModel.onPasswordChanged("secret")

            viewModel.signIn()

            assertTrue(!viewModel.state.value.isSubmitting)
            assertNull(viewModel.state.value.error)
            assertEquals("", viewModel.state.value.password)
        }

    @Test
    fun `a failed sign-in surfaces a generic INVALID_CREDENTIALS error, never the raw message`() =
        runTest {
            val viewModel = viewModel(FailingAuthRepository("some raw backend detail"))

            viewModel.signIn()

            assertTrue(!viewModel.state.value.isSubmitting)
            assertEquals(SignInError.INVALID_CREDENTIALS, viewModel.state.value.error)
        }

    @Test
    fun `calling signIn while already submitting is a no-op`() =
        runTest {
            val viewModel = viewModel(FakeAuthRepository())
            viewModel.signIn()
            check(!viewModel.state.value.isSubmitting)

            viewModel.signIn()
            viewModel.signIn()

            assertTrue(!viewModel.state.value.isSubmitting)
        }

    @Test
    fun `a sign-up that creates a session immediately has no confirmation prompt`() =
        runTest {
            val viewModel = viewModel(FakeAuthRepository(initialUserId = UserId("new-user")))

            viewModel.signUp()

            assertTrue(!viewModel.state.value.signUpNeedsEmailConfirmation)
        }

    @Test
    fun `a sign-up that succeeds without creating a session surfaces the email-confirmation prompt`() =
        runTest {
            val viewModel = viewModel(ConfirmationPendingAuthRepository())

            viewModel.signUp()

            assertTrue(viewModel.state.value.signUpNeedsEmailConfirmation)
            assertNull(viewModel.state.value.error)
        }
}
