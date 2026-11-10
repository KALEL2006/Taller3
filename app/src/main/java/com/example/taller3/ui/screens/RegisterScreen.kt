package com.example.taller3.ui.screens

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.taller3.R
import com.example.taller3.data.AuthUiState
import com.example.taller3.ui.components.AppTextField
import com.example.taller3.ui.components.AuthScaffold
import com.example.taller3.ui.components.LoadingButton
import com.example.taller3.ui.components.StatusMessage

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

    AuthScaffold(
        title = title,
        subtitle = R.string.register_subtitle,
        footer = {
            TextButton(onClick = onBack, enabled = enabled) {
                val (label, icon) = if (state.needsProfile) {
                    R.string.sign_out to R.drawable.ic_logout
                } else {
                    R.string.register_back to R.drawable.ic_arrow_back
                }
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(label))
            }
        }
    ) {
        AppTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = R.string.register_name,
            icon = R.drawable.ic_person,
            enabled = enabled
        )
        AppTextField(
            value = state.identification,
            onValueChange = onIdentificationChange,
            label = R.string.register_identification,
            icon = R.drawable.ic_badge,
            enabled = enabled,
            keyboardType = KeyboardType.Number
        )
        AppTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = R.string.login_email,
            icon = R.drawable.ic_email,
            enabled = enabled,
            keyboardType = KeyboardType.Email,
            readOnly = state.needsProfile
        )

        if (!state.needsProfile) {
            AppTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = R.string.login_password,
                icon = R.drawable.ic_lock,
                enabled = enabled,
                keyboardType = KeyboardType.Password
            )
        }

        AppTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = R.string.register_phone,
            icon = R.drawable.ic_phone,
            enabled = enabled,
            keyboardType = KeyboardType.Phone
        )

        state.errorMessageRes?.let { message -> StatusMessage(message) }

        LoadingButton(
            text = when {
                state.isLoading -> R.string.register_saving
                state.needsProfile -> R.string.register_save_profile
                else -> R.string.register_title
            },
            onClick = onRegister,
            enabled = enabled,
            loading = state.isLoading,
            icon = R.drawable.ic_person_add
        )
    }
}
