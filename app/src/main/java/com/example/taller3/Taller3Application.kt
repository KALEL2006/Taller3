package com.example.taller3

import android.app.Application
import com.cloudinary.android.MediaManager

class Taller3Application : Application() {
    override fun onCreate() {
        super.onCreate()

        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        if (cloudName.isBlank()) return

        MediaManager.init(
            this,
            hashMapOf<String, Any>(
                "cloud_name" to cloudName,
                "secure" to true
            )
        )
    }
}