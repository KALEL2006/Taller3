package com.example.taller3.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
@GoogleMapComposable
fun ProfileMapMarker(
    position: LatLng,
    title: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val fill = color.toArgb()
    val border = MaterialTheme.colorScheme.surface.toArgb()
    val size = with(LocalDensity.current) { 36.dp.roundToPx().coerceAtLeast(1) }
    val icon = remember(fill, border, size) {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val center = size / 2f
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = border
        canvas.drawCircle(center, center, size * 0.44f, paint)
        paint.color = fill
        canvas.drawCircle(center, center, size * 0.34f, paint)

        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    Marker(
        state = rememberUpdatedMarkerState(position),
        title = title,
        icon = icon,
        anchor = Offset(0.5f, 0.5f)
    )
}
