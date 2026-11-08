package com.example.taller3.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.R
import com.example.taller3.data.AuthUiState
import com.example.taller3.data.model.UserProfile
import com.example.taller3.data.repository.ProfileRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val profiles = ProfileRepository()

    private val _uiState = MutableStateFlow(
        AuthUiState(isLoading = auth.currentUser != null)
    )
    val uiState = _uiState.asStateFlow()

    init {
        auth.currentUser?.let { user ->
            viewModelScope.launch { resolveSession(user) }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessageRes = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessageRes = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, errorMessageRes = null) }
    }

    fun onIdentificationChange(value: String) {
        _uiState.update { it.copy(identification = value, errorMessageRes = null) }
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value, errorMessageRes = null) }
    }

    fun clearForm() {
        _uiState.value = AuthUiState()
    }

    fun signInUser() {
        val state = _uiState.value
        if (state.isLoading) return

        val email = state.email.trim()
        if (email.isBlank() || state.password.isBlank()) {
            showError(R.string.auth_required_fields)
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }

        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, state.password).await()
                resolveSession(checkNotNull(result.user))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError(R.string.auth_sign_in_error)
            }
        }
    }

    fun registerUser() {
        val state = _uiState.value
        if (state.isLoading || state.isSignedIn) return

        val profile = UserProfile(
            name = state.name.trim(),
            identification = state.identification.trim(),
            email = state.email.trim(),
            phone = state.phone.trim()
        )
        val requiredFields = listOf(
            profile.name, profile.identification, profile.email, profile.phone
        )

        if (requiredFields.any { it.isBlank() } ||
            (!state.needsProfile && state.password.isBlank())
        ) {
            showError(R.string.register_required_fields)
            return
        }

        if (!state.needsProfile && state.password.length < 6) {
            showError(R.string.register_short_password)
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }

        viewModelScope.launch {
            try {
                val user = if (state.needsProfile) {
                    checkNotNull(auth.currentUser)
                } else {
                    val result = auth.createUserWithEmailAndPassword(
                        profile.email,
                        state.password
                    ).await()
                    checkNotNull(result.user)
                }

                val accountEmail = user.email ?: profile.email
                _uiState.update {
                    it.copy(email = accountEmail, password = "", needsProfile = true)
                }

                profiles.createProfile(user.uid, profile.copy(email = accountEmail))
                _uiState.value = AuthUiState(isSignedIn = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: FirebaseAuthUserCollisionException) {
                showError(R.string.register_email_in_use)
            } catch (_: Exception) {
                val message = if (_uiState.value.needsProfile) {
                    R.string.register_profile_error
                } else {
                    R.string.register_error
                }
                showError(message)
            }
        }
    }

    fun signOut() {
        auth.signOut()
        clearForm()
    }

    private suspend fun resolveSession(user: FirebaseUser) {
        try {
            _uiState.value = if (profiles.hasProfile(user.uid)) {
                AuthUiState(isSignedIn = true)
            } else {
                AuthUiState(email = user.email.orEmpty(), needsProfile = true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            auth.signOut()
            _uiState.value = AuthUiState(errorMessageRes = R.string.profile_check_error)
        }
    }

    private fun showError(@StringRes message: Int) {
        _uiState.update { it.copy(isLoading = false, errorMessageRes = message) }
    }
}