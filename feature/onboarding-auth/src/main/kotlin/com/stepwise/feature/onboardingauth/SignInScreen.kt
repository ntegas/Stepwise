package com.stepwise.feature.onboardingauth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stepwise.core.designsystem.component.StepwiseButton
import com.stepwise.core.designsystem.component.StepwiseOutlinedButton
import com.stepwise.core.designsystem.theme.StepwiseSpacing

@Composable
fun SignInRoute(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onSignIn = viewModel::signIn,
        onSignUp = viewModel::signUp,
    )
}

@Composable
fun SignInScreen(
    state: SignInUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(StepwiseSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.sign_in_app_name), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = { Text(stringResource(R.string.sign_in_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.large),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = { Text(stringResource(R.string.sign_in_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.small),
        )
        if (state.error != null) {
            Text(
                text = stringResource(state.error.toStringRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        }
        if (state.signUpNeedsEmailConfirmation) {
            Text(
                text = stringResource(R.string.sign_up_confirm_email),
                modifier = Modifier.padding(top = StepwiseSpacing.small),
            )
        }
        StepwiseButton(
            text = stringResource(R.string.sign_in_button),
            onClick = onSignIn,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.large),
        )
        StepwiseOutlinedButton(
            text = stringResource(R.string.sign_up_button),
            onClick = onSignUp,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = StepwiseSpacing.small),
        )
    }
}

private fun SignInError.toStringRes(): Int =
    when (this) {
        SignInError.INVALID_CREDENTIALS -> R.string.sign_in_error_auth
        SignInError.NETWORK -> R.string.sign_in_error_network
        SignInError.UNKNOWN -> R.string.sign_in_error_generic
    }
