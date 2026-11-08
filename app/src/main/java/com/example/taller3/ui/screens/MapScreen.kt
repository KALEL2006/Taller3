package com.example.taller3.ui.screens

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taller3.R
import com.example.taller3.location.hasLocationPermission
import com.example.taller3.ui.components.ProfileMapMarker
import com.example.taller3.ui.viewmodel.MapViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onSignOut: () -> Unit, viewModel: MapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connectedUsers by viewModel.connectedUsers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(4.6097, -74.0817), 12f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.startSharing()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            viewModel.stopSharing()
        }
    }

    LifecycleStartEffect(viewModel) {
        viewModel.startWatchingUsers()
        onStopOrDispose { viewModel.stopWatchingUsers() }
    }

    val routePositions = remember(state.route) {
        state.route.map { point -> LatLng(point.latitude, point.longitude) }
    }
    val firstPosition = routePositions.firstOrNull()
    LaunchedEffect(firstPosition) {
        if (firstPosition != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(firstPosition, 16f)
        }
    }

    val switchLabel = stringResource(R.string.map_share_location)
    val markerTitle = stringResource(R.string.map_my_location)
    val otherMarkerTitle = stringResource(R.string.map_other_user)
    val routeColor = MaterialTheme.colorScheme.primary
    val otherRouteColor = MaterialTheme.colorScheme.tertiary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(
                        onClick = { viewModel.signOut(onSignOut) },
                        enabled = !state.isDisconnecting
                    ) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(switchLabel, modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.isSharing,
                            enabled = !state.isDisconnecting && !state.needsDisconnectRetry,
                            modifier = Modifier.semantics { contentDescription = switchLabel },
                            onCheckedChange = { enabled ->
                                when {
                                    !enabled -> viewModel.stopSharing()
                                    hasLocationPermission(context) -> viewModel.startSharing()
                                    else -> permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        )
                    }

                    val status = when {
                        state.isDisconnecting -> R.string.map_stopping
                        state.isSharing && routePositions.isEmpty() -> R.string.map_waiting_for_fix
                        state.isSharing -> R.string.map_location_shared
                        else -> null
                    }
                    status?.let { Text(stringResource(it)) }

                    state.errorMessageRes?.let {
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                    }
                    if (state.needsDisconnectRetry) {
                        TextButton(onClick = viewModel::stopSharing) {
                            Text(stringResource(R.string.map_retry_disconnect))
                        }
                    }
                    connectedUsers.errorMessageRes?.let { message ->
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::startWatchingUsers) {
                            Text(stringResource(R.string.map_retry_other_users))
                        }
                    }
                }
            }

            GoogleMap(
                modifier = Modifier.fillMaxWidth().weight(1f),
                cameraPositionState = cameraPositionState
            ) {
                connectedUsers.users.forEach { user ->
                    key(user.uid) {
                        val points = remember(user.route) {
                            user.route.map { point -> LatLng(point.latitude, point.longitude) }
                        }
                        if (points.size > 1) {
                            Polyline(points = points, color = otherRouteColor, width = 8f)
                        }
                        ProfileMapMarker(
                            position = LatLng(user.position.latitude, user.position.longitude),
                            title = user.name.ifBlank { otherMarkerTitle },
                            color = otherRouteColor
                        )
                    }
                }
                if (routePositions.size > 1) {
                    Polyline(points = routePositions, color = routeColor, width = 10f)
                }
                routePositions.lastOrNull()?.let { position ->
                    ProfileMapMarker(position = position, title = markerTitle)
                }
            }
        }
    }
}