package com.example.taller3.data

import androidx.annotation.StringRes

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    @StringRes val errorMessageRes: Int? = null
)