package com.example.taller3.data.repository

import com.example.taller3.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class ProfileRepository {
    private val firestore = Firebase.firestore

    suspend fun hasProfile(uid: String): Boolean {
        return firestore.collection("profiles")
            .document(uid)
            .get()
            .await()
            .exists()
    }

    suspend fun createProfile(uid: String, profile: UserProfile) {
        val profileRef = firestore.collection("profiles").document(uid)
        val locationRef = firestore.collection("liveLocations").document(uid)

        firestore.runTransaction { transaction ->
            if (!transaction.get(profileRef).exists()) {
                val locationExists = transaction.get(locationRef).exists()

                transaction.set(profileRef, profile)

                if (!locationExists) {
                    transaction.set(
                        locationRef,
                        mapOf(
                            "name" to profile.name,
                            "connected" to false
                        )
                    )
                }
            }
            Unit
        }.await()
    }
}
