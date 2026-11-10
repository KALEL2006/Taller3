package com.example.taller3.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.taller3.R
import com.example.taller3.data.AuthUiState
import com.example.taller3.ui.components.AppTextField
import com.example.taller3.ui.components.AuthScaffold
import com.example.taller3.ui.components.LoadingButton
import com.example.taller3.ui.components.StatusMessage

@Composable
fun LoginScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onOpenRegister: () -> Unit
) {
    val enabled = !state.isLoading

    AuthScaffold(
        title = R.string.login_title,
        subtitle = R.string.login_subtitle,
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpenRegister, enabled = enabled) {
                    Text(stringResource(R.string.register_title))
                }
            }
        }
    ) {
        AppTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = R.string.login_email,
            icon = R.drawable.ic_email,
            enabled = enabled,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        )

        AppTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = R.string.login_password,
            icon = R.drawable.ic_lock,
            enabled = enabled,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSignIn() })
        )

        state.errorMessageRes?.let { message -> StatusMessage(message) }

        LoadingButton(
            text = if (state.isLoading) R.string.login_loading else R.string.login_title,
            onClick = onSignIn,
            enabled = enabled,
            loading = state.isLoading,
            icon = R.drawable.ic_login
        )
    }
}
