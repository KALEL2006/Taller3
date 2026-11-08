package com.example.taller3.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.taller3.R
import com.example.taller3.data.AuthUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AuthViewModel : ViewModel() {

    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(
        AuthUiState(isSignedIn = auth.currentUser != null)
    )
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessageRes = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessageRes = null) }
    }

    fun signInUser() {
        val state = _uiState.value
        if (state.isLoading) return

        val email = state.email.trim()
        if (email.isBlank() || state.password.isBlank()) {
            _uiState.update {
                it.copy(errorMessageRes = R.string.auth_required_fields)
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }

        auth.signInWithEmailAndPassword(email, state.password)
            .addOnCompleteListener { task ->
                _uiState.update { current ->
                    if (task.isSuccessful) {
                        current.copy(
                            password = "",
                            isLoading = false,
                            isSignedIn = true
                        )
                    } else {
                        current.copy(
                            isLoading = false,
                            errorMessageRes = R.string.auth_sign_in_error
                        )
                    }
                }
            }
    }
}