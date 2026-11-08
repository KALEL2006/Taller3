package com.example.taller3.data

import androidx.annotation.StringRes
import com.example.taller3.data.model.UserProfile
import java.io.File

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isChangingPassword: Boolean = false,
    val isSavingPhoto: Boolean = false,
    val profileMessage: ProfileMessage? = null,
    val passwordMessage: ProfileMessage? = null,
    val photoMessage: ProfileMessage? = null,
    val pendingPhoto: File? = null,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = ""
) {
    val isSaving: Boolean
        get() = isSavingProfile || isChangingPassword || isSavingPhoto
}

data class ProfileMessage(
    @StringRes val textRes: Int,
    val isError: Boolean = true
)