package com.example.taller3.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.taller3.R
import com.example.taller3.data.AuthUiState

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onNameChange: (String) -> Unit,
    onIdentificationChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit
) {
    val enabled = !state.isLoading
    val title = if (state.needsProfile) {
        R.string.register_complete_profile
    } else {
        R.string.register_title
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineMedium
        )

        RegisterField(R.string.register_name, state.name, onNameChange, enabled)
        RegisterField(
            R.string.register_identification,
            state.identification,
            onIdentificationChange,
            enabled,
            keyboardType = KeyboardType.Number
        )
        RegisterField(
            R.string.login_email,
            state.email,
            onEmailChange,
            enabled,
            keyboardType = KeyboardType.Email,
            readOnly = state.needsProfile
        )

        if (!state.needsProfile) {
            RegisterField(
                R.string.login_password,
                state.password,
                onPasswordChange,
                enabled,
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation()
            )
        }

        RegisterField(
            R.string.register_phone,
            state.phone,
            onPhoneChange,
            enabled,
            keyboardType = KeyboardType.Phone
        )

        state.errorMessageRes?.let { message ->
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = onRegister,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            val label = when {
                state.isLoading -> R.string.register_saving
                state.needsProfile -> R.string.register_save_profile
                else -> R.string.register_title
            }
            Text(stringResource(label))
        }

        TextButton(onClick = onBack, enabled = enabled) {
            val label = if (state.needsProfile) R.string.sign_out else R.string.register_back
            Text(stringResource(label))
        }
    }
}

@Composable
private fun RegisterField(
    @StringRes label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation
    )
}