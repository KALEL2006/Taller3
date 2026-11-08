package com.example.taller3.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.R
import com.example.taller3.data.ProfileMessage
import com.example.taller3.data.ProfileUiState
import com.example.taller3.data.model.UserProfile
import com.example.taller3.data.repository.ProfilePhotoRepository
import com.example.taller3.data.repository.ProfileRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val profiles = ProfileRepository()
    private val photos = ProfilePhotoRepository()
    private var uploadedPhotoUrl: String? = null
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        _uiState.update { it.copy(isLoading = true, profileMessage = null) }

        viewModelScope.launch {
            try {
                val user = checkNotNull(auth.currentUser)
                val profile = profiles.getProfile(user.uid)
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profileMessage = ProfileMessage(R.string.profile_load_error)
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = editProfile { it.copy(name = value) }

    fun onIdentificationChange(value: String) =
        editProfile { it.copy(identification = value) }

    fun onPhoneChange(value: String) = editProfile { it.copy(phone = value) }

    fun onCurrentPasswordChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, passwordMessage = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, passwordMessage = null) }
    }

    fun onPhotoCaptured(photo: File) {
        _uiState.value.pendingPhoto?.delete()
        uploadedPhotoUrl = null
        _uiState.update { it.copy(pendingPhoto = photo, photoMessage = null) }
    }

    fun onCameraError() {
        _uiState.update {
            it.copy(photoMessage = ProfileMessage(R.string.profile_camera_error))
        }
    }

    fun savePhoto(context: Context) {
        val state = _uiState.value
        val photo = state.pendingPhoto ?: return
        if (state.profile == null || state.isLoading || state.isSaving) return

        if (!photos.isConfigured) {
            _uiState.update {
                it.copy(photoMessage = ProfileMessage(R.string.profile_photo_configuration_error))
            }
            return
        }

        val appContext = context.applicationContext
        _uiState.update { it.copy(isSavingPhoto = true, photoMessage = null) }
        viewModelScope.launch {
            try {
                val uid = checkNotNull(auth.currentUser).uid
                val url = uploadedPhotoUrl ?: photos.upload(appContext, photo).also {
                    uploadedPhotoUrl = it
                }
                check(auth.currentUser?.uid == uid)
                profiles.updatePhoto(uid, url)

                uploadedPhotoUrl = null
                _uiState.update {
                    it.copy(
                        profile = it.profile?.copy(photoUrl = url),
                        pendingPhoto = null,
                        isSavingPhoto = false,
                        photoMessage = ProfileMessage(R.string.profile_photo_saved, isError = false)
                    )
                }
                photo.delete()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val errorRes = if (uploadedPhotoUrl == null) {
                    R.string.profile_photo_upload_error
                } else {
                    R.string.profile_photo_save_error
                }
                _uiState.update {
                    it.copy(isSavingPhoto = false, photoMessage = ProfileMessage(errorRes))
                }
            }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        val draft = state.profile ?: return
        if (state.isLoading || state.isSaving) return

        val profile = draft.copy(
            name = draft.name.trim(),
            identification = draft.identification.trim(),
            phone = draft.phone.trim()
        )
        if (profile.name.isBlank() || profile.identification.isBlank() || profile.phone.isBlank()) {
            _uiState.update {
                it.copy(profileMessage = ProfileMessage(R.string.profile_required_fields))
            }
            return
        }

        _uiState.update { it.copy(isSavingProfile = true, profileMessage = null) }

        viewModelScope.launch {
            try {
                val uid = checkNotNull(auth.currentUser).uid
                profiles.updateProfileDetails(uid, profile)
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isSavingProfile = false,
                        profileMessage = ProfileMessage(R.string.profile_saved, isError = false)
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        profileMessage = ProfileMessage(R.string.profile_save_error)
                    )
                }
            }
        }
    }

    fun changePassword() {
        val state = _uiState.value
        if (state.profile == null || state.isLoading || state.isSaving) return

        val errorRes = when {
            state.currentPassword.isBlank() -> R.string.profile_current_password_required
            state.newPassword.length < 6 || state.newPassword.isBlank() ->
                R.string.register_short_password
            state.newPassword != state.confirmPassword -> R.string.profile_passwords_mismatch
            else -> null
        }
        if (errorRes != null) {
            _uiState.update { it.copy(passwordMessage = ProfileMessage(errorRes)) }
            return
        }

        _uiState.update { it.copy(isChangingPassword = true, passwordMessage = null) }

        viewModelScope.launch {
            val message = try {
                val user = checkNotNull(auth.currentUser)
                val credential = EmailAuthProvider.getCredential(
                    checkNotNull(user.email),
                    state.currentPassword
                )
                user.reauthenticate(credential).await()
                user.updatePassword(state.newPassword).await()
                ProfileMessage(R.string.profile_password_changed, isError = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: FirebaseAuthWeakPasswordException) {
                ProfileMessage(R.string.profile_weak_password)
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                ProfileMessage(R.string.profile_wrong_password)
            } catch (_: Exception) {
                ProfileMessage(R.string.profile_password_error)
            }

            _uiState.update {
                if (message.isError) {
                    it.copy(isChangingPassword = false, passwordMessage = message)
                } else {
                    it.copy(
                        isChangingPassword = false,
                        passwordMessage = message,
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )
                }
            }
        }
    }

    private fun editProfile(edit: (UserProfile) -> UserProfile) {
        _uiState.update { state ->
            state.copy(profile = state.profile?.let(edit), profileMessage = null)
        }
    }

    override fun onCleared() {
        _uiState.value.pendingPhoto?.delete()
        super.onCleared()
    }
}