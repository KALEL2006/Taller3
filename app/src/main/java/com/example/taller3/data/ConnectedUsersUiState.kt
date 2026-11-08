package com.example.taller3.data

import androidx.annotation.StringRes
import com.example.taller3.data.model.LiveLocation

data class ConnectedUsersUiState(
    val users: List<LiveLocation> = emptyList(),
    @StringRes val errorMessageRes: Int? = null
)
