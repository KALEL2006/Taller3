package com.example.taller3.data.repository

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.taller3.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ProfilePhotoRepository {
    val isConfigured: Boolean
        get() = BuildConfig.CLOUDINARY_CLOUD_NAME.isNotBlank() &&
                BuildConfig.CLOUDINARY_UPLOAD_PRESET.isNotBlank()

    suspend fun upload(context: Context, photo: File): String {
        val appContext = context.applicationContext
        val request = ImageRequest.Builder(appContext)
            .data(photo)
            .size(768, 768)
            .scale(Scale.FIT)
            .precision(Precision.EXACT)
            .allowHardware(false)
            .build()
        val result = appContext.imageLoader.execute(request) as? SuccessResult
            ?: throw IOException("Could not decode profile photo")

        val imageBytes = withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { output ->
                if (!result.image.toBitmap().compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    throw IOException("Could not encode profile photo")
                }
                output.toByteArray()
            }
        }

        val completion = CompletableDeferred<String>()
        val mediaManager = MediaManager.get()
        val callback = object : UploadCallback {
            override fun onStart(requestId: String) = Unit

            override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) = Unit

            override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                val url = resultData["secure_url"] as? String
                if (url != null && url.startsWith("https://")) {
                    completion.complete(url)
                } else {
                    completion.completeExceptionally(IOException("Missing secure upload URL"))
                }
            }

            override fun onError(requestId: String, error: ErrorInfo) {
                completion.completeExceptionally(IOException(error.description))
            }

            override fun onReschedule(requestId: String, error: ErrorInfo) {
                completion.completeExceptionally(IOException(error.description))
            }
        }
        val requestId = mediaManager.upload(imageBytes)
            .unsigned(BuildConfig.CLOUDINARY_UPLOAD_PRESET)
            .callback(callback)
            .startNow(appContext)

        try {
            return withTimeoutOrNull(60_000L) { completion.await() }
                ?: throw IOException("Photo upload timed out")
        } finally {
            if (!completion.isCompleted) {
                mediaManager.cancelRequest(requestId)
            }
        }
    }
}