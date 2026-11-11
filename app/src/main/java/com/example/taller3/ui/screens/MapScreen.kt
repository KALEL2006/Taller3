package com.example.taller3.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taller3.R
import com.example.taller3.location.hasLocationPermission
import com.example.taller3.ui.components.BrandLogo
import com.example.taller3.ui.components.HintText
import com.example.taller3.ui.components.IconBadge
import com.example.taller3.ui.components.ProfileMapMarker
import com.example.taller3.ui.components.StatusMessage
import com.example.taller3.ui.viewmodel.MapViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen(
    onSignOut: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connectedUsers by viewModel.connectedUsers.collectAsStateWithLifecycle()
    val ownPhotoUrl by viewModel.ownPhotoUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(4.6097, -74.0817), 12f)
    }
    var centeredRouteStart by rememberSaveable { mutableStateOf<LatLng?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.startSharing()
        } else {
            viewModel.onPermissionDenied()
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
        if (firstPosition != centeredRouteStart) {
            centeredRouteStart = firstPosition
            if (firstPosition != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(firstPosition, 16f)
            }
        }
    }

    val switchLabel = stringResource(R.string.map_share_location)
    val markerTitle = stringResource(R.string.map_my_location)
    val otherMarkerTitle = stringResource(R.string.map_other_user)
    val routeColor = colors.primary
    val otherRouteColor = colors.tertiary
    val routeBorderColor = colors.surfaceContainerLowest

    val darkTheme = isSystemInDarkTheme()
    val mapProperties = remember(darkTheme) {
        MapProperties(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                context,
                if (darkTheme) R.raw.map_style_dark else R.raw.map_style_light
            )
        )
    }
    val mapUiSettings = remember {
        MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            contentPadding = PaddingValues(
                top = with(density) { overlayHeightPx.toDp() },
                bottom = 88.dp
            )
        ) {
            connectedUsers.users.forEach { user ->
                key(user.uid) {
                    val points = remember(user.route) {
                        user.route.map { point -> LatLng(point.latitude, point.longitude) }
                    }
                    if (points.size > 1) {
                        RouteLine(points, otherRouteColor, routeBorderColor, width = 9f)
                    }
                    ProfileMapMarker(
                        position = LatLng(user.position.latitude, user.position.longitude),
                        title = user.name.ifBlank { otherMarkerTitle },
                        color = otherRouteColor,
                        photoUrl = user.photoUrl
                    )
                }
            }
            if (routePositions.size > 1) {
                RouteLine(routePositions, routeColor, routeBorderColor, width = 11f)
            }
            routePositions.lastOrNull()?.let { position ->
                ProfileMapMarker(
                    position = position,
                    title = markerTitle,
                    photoUrl = ownPhotoUrl,
                    zIndex = 1f
                )
            }
        }

        // Controles flotantes en la parte superior
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onSizeChanged { overlayHeightPx = it.height },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerLowest,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BrandLogo(size = 32.dp)
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    Surface(
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        color = colors.surfaceContainerLowest
                    ) {
                        IconButton(
                            onClick = { menuOpen = true },
                            enabled = !state.isDisconnecting
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.map_menu)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_edit)) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onOpenProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sign_out)) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_logout), contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.signOut(onSignOut)
                            },
                            enabled = !state.isDisconnecting
                        )
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLowest),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val badgeColor by animateColorAsState(
                        if (state.isSharing) colors.primary else colors.surfaceContainerHighest,
                        label = "badgeColor"
                    )
                    val badgeContent by animateColorAsState(
                        if (state.isSharing) colors.onPrimary else colors.onSurfaceVariant,
                        label = "badgeContent"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconBadge(
                            icon = R.drawable.ic_location_on,
                            size = 44.dp,
                            containerColor = badgeColor,
                            contentColor = badgeContent
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(switchLabel, style = MaterialTheme.typography.titleSmall)

                            val status = when {
                                state.isDisconnecting -> R.string.map_stopping
                                state.isSharing && routePositions.isEmpty() -> R.string.map_waiting_for_fix
                                state.isSharing -> R.string.map_location_shared
                                state.needsDisconnectRetry -> null
                                else -> R.string.map_disconnected
                            }
                            status?.let {
                                val dotColor by animateColorAsState(
                                    when {
                                        state.isDisconnecting -> colors.tertiary
                                        state.isSharing -> colors.primary
                                        else -> colors.outline
                                    },
                                    label = "dotColor"
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Text(
                                        text = stringResource(it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = state.isSharing,
                            enabled = !state.isDisconnecting && !state.needsDisconnectRetry,
                            modifier = Modifier.semantics { contentDescription = switchLabel },
                            thumbContent = if (state.isSharing) {
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_location_on),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            },
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

                    AnimatedVisibility(
                        visible = !state.isSharing && !state.isDisconnecting &&
                                !state.needsDisconnectRetry && state.errorMessageRes == null
                    ) {
                        HintText(R.string.map_status_hint_off)
                    }

                    state.errorMessageRes?.let { StatusMessage(it) }
                    if (state.needsDisconnectRetry) {
                        RetryButton(R.string.map_retry_disconnect, viewModel::stopSharing)
                    }
                    connectedUsers.errorMessageRes?.let { message ->
                        StatusMessage(message)
                        RetryButton(R.string.map_retry_other_users, viewModel::startWatchingUsers)
                    }
                }
            }
        }

        // Contador de usuarios conectados
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .widthIn(max = 360.dp),
            shape = CircleShape,
            color = colors.surfaceContainerLowest,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconBadge(
                    icon = R.drawable.ic_group,
                    size = 32.dp,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer
                )
                val count = connectedUsers.users.size
                Text(
                    text = pluralStringResource(R.plurals.map_other_users_online, count, count),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
@GoogleMapComposable
private fun RouteLine(points: List<LatLng>, color: Color, borderColor: Color, width: Float) {
    Polyline(
        points = points,
        color = borderColor,
        width = width + 6f,
        startCap = RoundCap(),
        endCap = RoundCap(),
        jointType = JointType.ROUND
    )
    Polyline(
        points = points,
        color = color,
        width = width,
        startCap = RoundCap(),
        endCap = RoundCap(),
        jointType = JointType.ROUND
    )
}

@Composable
private fun RetryButton(text: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(text))
    }
}