package com.example.taller3.data.repository

import com.example.taller3.data.model.LiveLocation
import com.example.taller3.data.model.RoutePoint
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TrackingRepository {
    private val firestore = Firebase.firestore
    private val locations = firestore.collection("liveLocations")

    fun observeConnectedLocations(): Flow<List<LiveLocation>> {
        return locations.whereEqualTo("connected", true)
            .limit(100L)
            .confirmedSnapshots()
            .map { snapshot ->
                snapshot?.documents.orEmpty().mapNotNull { document ->
                    val position = document.toRoutePoint() ?: return@mapNotNull null
                    LiveLocation(
                        uid = document.id,
                        name = (document.get("name") as? String).orEmpty(),
                        position = position,
                        routeId = (document.get("routeId") as? String)
                            ?.takeIf { it.isNotBlank() && '/' !in it }
                    )
                }
            }
            .distinctUntilChanged()
    }

    fun observeRoute(uid: String, routeId: String): Flow<List<RoutePoint>> {
        return routePoints(uid, routeId).orderBy("recordedAt")
            .confirmedSnapshots()
            .map { snapshot ->
                snapshot?.documents.orEmpty().mapNotNull { it.toRoutePoint() }
            }
            .distinctUntilChanged()
    }

    suspend fun publishPoint(
        uid: String,
        routeId: String,
        point: RoutePoint,
        isFirstPoint: Boolean
    ): Boolean {
        val locationRef = locations.document(uid)
        val pointRef = routePoints(uid, routeId).document()

        return firestore.runTransaction { transaction ->
            val current = transaction.get(locationRef)
            val ownsActiveRoute = current.getString("routeId") == routeId &&
                    current.getBoolean("connected") == true
            if (!isFirstPoint && !ownsActiveRoute) return@runTransaction false

            transaction.set(
                locationRef,
                mapOf(
                    "connected" to true,
                    "latitude" to point.latitude,
                    "longitude" to point.longitude,
                    "routeId" to routeId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                pointRef,
                mapOf(
                    "latitude" to point.latitude,
                    "longitude" to point.longitude,
                    "recordedAt" to FieldValue.serverTimestamp()
                )
            )
            true
        }.await()
    }

    suspend fun disconnect(uid: String, routeId: String) {
        val locationRef = locations.document(uid)
        firestore.runTransaction { transaction ->
            if (transaction.get(locationRef).getString("routeId") == routeId) {
                transaction.update(
                    locationRef,
                    mapOf(
                        "connected" to false,
                        "latitude" to FieldValue.delete(),
                        "longitude" to FieldValue.delete(),
                        "routeId" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            Unit
        }.await()

        val points = routePoints(uid, routeId)
        while (true) {
            val page = points.limit(200L).get(Source.SERVER).await()
            if (page.isEmpty) return

            val batch = firestore.batch()
            page.documents.forEach { document -> batch.delete(document.reference) }
            batch.commit().await()
        }
    }

    private fun routePoints(uid: String, routeId: String) =
        locations.document(uid).collection("routes").document(routeId).collection("points")

    private fun DocumentSnapshot.toRoutePoint(): RoutePoint? {
        val latitude = (get("latitude") as? Number)?.toDouble() ?: return null
        val longitude = (get("longitude") as? Number)?.toDouble() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return RoutePoint(latitude, longitude)
    }

    private fun Query.confirmedSnapshots(): Flow<QuerySnapshot?> = callbackFlow<QuerySnapshot?> {
        val listener = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.takeUnless { it.metadata.isFromCache })
            }
        }
        awaitClose { listener.remove() }
    }.conflate()
}
