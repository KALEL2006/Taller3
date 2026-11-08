package com.example.taller3.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
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
    color: Color = MaterialTheme.colorScheme.primary,
    photoUrl: String? = null
) {
    val context = LocalContext.current
    val size = with(LocalDensity.current) { 46.dp.roundToPx().coerceAtLeast(1) }
    val photo by key(photoUrl, size) {
        produceState<Bitmap?>(initialValue = null) {
            if (!photoUrl.isNullOrBlank()) {
                val request = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .size(size, size)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                value = result?.image?.toBitmap()
            }
        }
    }
    val ringColor = color.toArgb()
    val borderColor = MaterialTheme.colorScheme.surface.toArgb()
    val icon = remember(photo, ringColor, borderColor, size) {
        BitmapDescriptorFactory.fromBitmap(
            drawProfileMarker(size, ringColor, borderColor, photo)
        )
    }

    Marker(
        state = rememberUpdatedMarkerState(position),
        title = title,
        icon = icon,
        anchor = Offset(0.5f, 0.5f)
    )
}

private fun drawProfileMarker(
    size: Int,
    ringColor: Int,
    borderColor: Int,
    photo: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    paint.color = borderColor
    canvas.drawCircle(center, center, size * 0.48f, paint)
    paint.color = ringColor
    canvas.drawCircle(center, center, size * 0.43f, paint)

    if (photo != null) {
        val radius = size * 0.37f
        val scale = 2f * radius / minOf(photo.width, photo.height)
        val crop = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                center - photo.width * scale / 2f,
                center - photo.height * scale / 2f
            )
        }
        paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(crop)
        }
        canvas.drawCircle(center, center, radius, paint)
    }

    return bitmap
}
