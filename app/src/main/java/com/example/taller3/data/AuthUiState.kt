package com.example.taller3.data

import androidx.annotation.StringRes

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val identification: String = "",
    val phone: String = "",
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val needsProfile: Boolean = false,
    @StringRes val errorMessageRes: Int? = null
)