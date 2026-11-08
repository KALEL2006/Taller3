package com.example.taller3.data

import androidx.annotation.StringRes
import com.example.taller3.data.model.RoutePoint

data class MapUiState(
    val isSharing: Boolean = false,
    val isDisconnecting: Boolean = false,
    val needsDisconnectRetry: Boolean = false,
    val route: List<RoutePoint> = emptyList(),
    @StringRes val errorMessageRes: Int? = null
)