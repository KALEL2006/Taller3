package com.example.taller3.ui.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3.R
import com.example.taller3.data.ConnectedUsersUiState
import com.example.taller3.data.MapUiState
import com.example.taller3.data.model.LiveLocation
import com.example.taller3.data.repository.ProfileRepository
import com.example.taller3.data.repository.TrackingRepository
import com.example.taller3.location.locationUpdates
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val trackingRepository = TrackingRepository()
    private val profiles = ProfileRepository()
    private val auth = Firebase.auth
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState = _uiState.asStateFlow()
    private val _connectedUsers = MutableStateFlow(ConnectedUsersUiState())
    val connectedUsers = _connectedUsers.asStateFlow()
    private val _ownPhotoUrl = MutableStateFlow<String?>(null)
    val ownPhotoUrl = _ownPhotoUrl.asStateFlow()

    private var session: SharingSession? = null
    private var trackingJob: Job? = null
    private var disconnectJob: Job? = null
    private var connectedUsersJob: Job? = null
    private var ownPhotoJob: Job? = null

    fun startWatchingUsers() {
        val ownUid = auth.currentUser?.uid ?: return

        if (ownPhotoJob?.isActive != true) {
            ownPhotoJob = viewModelScope.launch {
                profiles.observePhotoUrl(ownUid)
                    .catch { emit(null) }
                    .collect { _ownPhotoUrl.value = it }
            }
        }
        if (connectedUsersJob?.isActive == true) return

        _connectedUsers.value = ConnectedUsersUiState()
        connectedUsersJob = viewModelScope.launch {
            val routeJobs = mutableMapOf<RouteKey, Job>()
            try {
                trackingRepository.observeConnectedLocations().collect { locations ->
                    val otherUsers = locations.filterNot { it.uid == ownUid }
                    val activeRoutes = otherUsers.mapNotNull { user ->
                        user.routeId?.let { RouteKey(user.uid, it) }
                    }.toSet()

                    (routeJobs.keys - activeRoutes).forEach { key ->
                        routeJobs.remove(key)?.cancel()
                    }

                    _connectedUsers.update { previous ->
                        val previousByUid = previous.users.associateBy(LiveLocation::uid)
                        previous.copy(users = otherUsers.map { user ->
                            val previousUser = previousByUid[user.uid]
                            if (previousUser != null && previousUser.routeId == user.routeId) {
                                user.copy(route = previousUser.route)
                            } else {
                                user
                            }
                        })
                    }

                    activeRoutes.forEach { key ->
                        if (routeJobs[key]?.isActive != true) {
                            routeJobs[key] = launch { watchRoute(key) }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _connectedUsers.value = ConnectedUsersUiState(
                    errorMessageRes = R.string.map_error_other_users
                )
            } finally {
                routeJobs.values.forEach { it.cancel() }
            }
        }
    }

    fun stopWatchingUsers() {
        connectedUsersJob?.cancel()
        connectedUsersJob = null
        ownPhotoJob?.cancel()
        ownPhotoJob = null
        _connectedUsers.value = ConnectedUsersUiState()
        _ownPhotoUrl.value = null
    }

    private suspend fun watchRoute(key: RouteKey) {
        trackingRepository.observeRoute(key.uid, key.routeId)
            .catch { emit(emptyList()) }
            .collect { points ->
                _connectedUsers.update { state ->
                    state.copy(users = state.users.map { user ->
                        if (user.uid == key.uid && user.routeId == key.routeId) {
                            user.copy(route = points)
                        } else {
                            user
                        }
                    })
                }
            }
    }

    fun startSharing() {
        if (session != null || disconnectJob?.isActive == true) return
        val uid = auth.currentUser?.uid ?: return
        val current = SharingSession(uid, UUID.randomUUID().toString())

        session = current
        _uiState.value = MapUiState(isSharing = true)
        trackingJob = viewModelScope.launch {
            var isFirstPoint = true
            try {
                locationUpdates(getApplication<Application>()).collect { point ->
                    current.mayHavePublished = true
                    val published = withContext(NonCancellable) {
                        trackingRepository.publishPoint(
                            uid = current.uid,
                            routeId = current.routeId,
                            point = point,
                            isFirstPoint = isFirstPoint
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    check(published) { "The active route changed" }

                    isFirstPoint = false
                    _uiState.update { it.copy(route = it.route + point) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                disconnect(messageRes = R.string.map_error_sharing)
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(errorMessageRes = R.string.map_permission_denied) }
    }

    fun stopSharing() {
        disconnect()
    }

    fun signOut(onSignOut: () -> Unit) {
        disconnect(onDisconnected = {
            stopWatchingUsers()
            onSignOut()
        })
    }

    private fun disconnect(
        @StringRes messageRes: Int? = null,
        onDisconnected: (() -> Unit)? = null
    ) {
        if (disconnectJob?.isActive == true) return

        val current = session
        if (current == null) {
            if (onDisconnected != null) {
                _uiState.value = MapUiState()
                onDisconnected()
            }
            return
        }

        val activeJob = trackingJob
        _uiState.value = MapUiState(isDisconnecting = true)
        disconnectJob = viewModelScope.launch {
            withContext(NonCancellable) {
                activeJob?.cancelAndJoin()
                trackingJob = null

                try {
                    if (current.mayHavePublished) {
                        trackingRepository.disconnect(current.uid, current.routeId)
                    }
                } catch (_: Exception) {
                    _uiState.value = MapUiState(
                        needsDisconnectRetry = true,
                        errorMessageRes = R.string.map_error_disconnect
                    )
                    return@withContext
                }

                session = null
                _uiState.value = MapUiState(errorMessageRes = messageRes)
                onDisconnected?.invoke()
            }
        }
    }

    private data class SharingSession(
        val uid: String,
        val routeId: String,
        var mayHavePublished: Boolean = false
    )

    private data class RouteKey(val uid: String, val routeId: String)
}