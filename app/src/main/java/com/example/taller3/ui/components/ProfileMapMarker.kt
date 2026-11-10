package com.example.taller3.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.Drawable
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
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.taller3.R
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * Marcador personalizado en forma de pin con la foto de perfil del usuario.
 * Si el usuario no tiene foto se muestra un avatar genérico del color del pin.
 */
@Composable
@GoogleMapComposable
fun ProfileMapMarker(
    position: LatLng,
    title: String,
    color: Color = MaterialTheme.colorScheme.primary,
    photoUrl: String? = null,
    zIndex: Float = 0f
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val width = with(density) { 52.dp.roundToPx().coerceAtLeast(1) }
    val height = with(density) { 66.dp.roundToPx().coerceAtLeast(1) }
    val photoSize = (width * 0.7f).toInt().coerceAtLeast(1)

    val photo by key(photoUrl, photoSize) {
        produceState<Bitmap?>(initialValue = null) {
            if (!photoUrl.isNullOrBlank()) {
                val request = ImageRequest.Builder(context)
                    .data(photoUrl)
                    .size(photoSize, photoSize)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                value = result?.image?.toBitmap()
            }
        }
    }
    val pinColor = color.toArgb()
    val borderColor = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
    val icon = remember(photo, pinColor, borderColor, width, height) {
        val placeholder = ContextCompat.getDrawable(context, R.drawable.ic_person)?.mutate()
        BitmapDescriptorFactory.fromBitmap(
            drawPinMarker(width, height, pinColor, borderColor, photo, placeholder)
        )
    }

    Marker(
        state = rememberUpdatedMarkerState(position),
        title = title,
        icon = icon,
        anchor = Offset(0.5f, PIN_TIP_RATIO),
        zIndex = zIndex
    )
}

private const val PIN_TIP_RATIO = 0.96f

private fun drawPinMarker(
    width: Int,
    height: Int,
    pinColor: Int,
    borderColor: Int,
    photo: Bitmap?,
    placeholder: Drawable?
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val shadowPad = width * 0.08f
    val radius = width / 2f - shadowPad
    val cx = width / 2f
    val cy = shadowPad + radius
    val tipY = height * PIN_TIP_RATIO

    // Silueta del pin (círculo + punta) con sombra suave
    val pin = Path().apply {
        addCircle(cx, cy, radius, Path.Direction.CW)
        moveTo(cx - radius * 0.62f, cy + radius * 0.72f)
        quadTo(cx - radius * 0.2f, tipY - radius * 0.35f, cx, tipY)
        quadTo(cx + radius * 0.2f, tipY - radius * 0.35f, cx + radius * 0.62f, cy + radius * 0.72f)
        close()
    }
    paint.color = pinColor
    paint.setShadowLayer(shadowPad, 0f, shadowPad * 0.45f, 0x66000000)
    canvas.drawPath(pin, paint)
    paint.clearShadowLayer()

    // Borde claro alrededor de la foto
    paint.color = borderColor
    canvas.drawCircle(cx, cy, radius * 0.84f, paint)

    val photoRadius = radius * 0.74f
    if (photo != null) {
        val scale = 2f * photoRadius / minOf(photo.width, photo.height)
        val crop = Matrix().apply {
            setScale(scale, scale)
            postTranslate(cx - photo.width * scale / 2f, cy - photo.height * scale / 2f)
        }
        paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(crop)
        }
        canvas.drawCircle(cx, cy, photoRadius, paint)
        paint.shader = null
    } else {
        paint.color = ColorUtils.blendARGB(borderColor, pinColor, 0.18f)
        canvas.drawCircle(cx, cy, photoRadius, paint)
        placeholder?.let { drawable ->
            val half = photoRadius * 0.7f
            drawable.setTint(pinColor)
            drawable.setBounds(
                (cx - half).toInt(),
                (cy - half).toInt(),
                (cx + half).toInt(),
                (cy + half).toInt()
            )
            drawable.draw(canvas)
        }
    }

    // Punto en la base del pin
    paint.color = borderColor
    canvas.drawCircle(cx, tipY - shadowPad * 0.2f, shadowPad * 0.35f, paint)

    return bitmap
}
