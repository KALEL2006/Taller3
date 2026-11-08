package com.example.taller3.data.repository

import com.example.taller3.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    suspend fun getProfile(uid: String): UserProfile {
        val snapshot = firestore.collection("profiles").document(uid).get().await()
        return checkNotNull(snapshot.toObject(UserProfile::class.java))
    }

    suspend fun updateProfileDetails(uid: String, profile: UserProfile) {
        val fields = mapOf(
            "name" to profile.name,
            "identification" to profile.identification,
            "phone" to profile.phone
        )
        updateProfile(uid, fields, mapOf("name" to profile.name))
    }

    suspend fun updatePhoto(uid: String, photoUrl: String) {
        val fields = mapOf("photoUrl" to photoUrl)
        updateProfile(uid, fields, fields)
    }

    fun observePhotoUrl(uid: String): Flow<String?> = callbackFlow<String?> {
        val listener = firestore.collection("profiles").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.get("photoUrl") as? String)
                }
            }
        awaitClose { listener.remove() }
    }.distinctUntilChanged()

    private suspend fun updateProfile(
        uid: String,
        fields: Map<String, String>,
        sharedFields: Map<String, String>
    ) {
        val profileRef = firestore.collection("profiles").document(uid)
        val locationRef = firestore.collection("liveLocations").document(uid)

        firestore.runTransaction { transaction ->
            val locationExists = transaction.get(locationRef).exists()
            val nameForNewLocation = if (locationExists) {
                null
            } else {
                fields["name"] ?: transaction.get(profileRef).getString("name").orEmpty()
            }

            transaction.update(profileRef, fields)
            if (locationExists) {
                transaction.update(locationRef, sharedFields)
            } else {
                transaction.set(
                    locationRef,
                    mapOf("name" to nameForNewLocation.orEmpty(), "connected" to false) +
                            sharedFields
                )
            }
            Unit
        }.await()
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