package com.example.taller3.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.taller3.data.model.RoutePoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
internal fun locationUpdates(context: Context): Flow<RoutePoint> = callbackFlow {
    if (!hasLocationPermission(context)) {
        close(SecurityException("Foreground location permission is required"))
        return@callbackFlow
    }

    val client = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
        .setMinUpdateDistanceMeters(3f)
        .build()
    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                trySend(RoutePoint(location.latitude, location.longitude))
            }
        }
    }

    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        .addOnFailureListener { error -> close(error) }

    awaitClose { client.removeLocationUpdates(callback) }
}.distinctUntilChanged()