package com.example.taller3.data.repository

import android.content.Context
import android.os.SystemClock
import com.example.taller3.data.model.LiveLocation
import com.example.taller3.data.model.RoutePoint
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TrackingRepository(context: Context) {
    private val firestore = Firebase.firestore
    private val locations = firestore.collection("liveLocations")
    private val pendingRoutes = context.applicationContext.getSharedPreferences(
        "pending_tracking_routes",
        Context.MODE_PRIVATE
    )

    fun pendingRoute(uid: String): String? {
        return pendingRoutes.getString(uid, null)
            ?.takeIf { it.isNotBlank() && '/' !in it }
    }

    suspend fun rememberRoute(uid: String, routeId: String) = withContext(Dispatchers.IO) {
        check(pendingRoutes.edit().putString(uid, routeId).commit())
    }

    suspend fun forgetRoute(uid: String, routeId: String) = withContext(Dispatchers.IO) {
        if (pendingRoutes.getString(uid, null) == routeId) {
            check(pendingRoutes.edit().remove(uid).commit())
        }
    }

    fun observeConnectedLocations(): Flow<List<LiveLocation>> {
        return locations.whereEqualTo("connected", true)
            .limit(100L)
            .confirmedSnapshots()
            .runningFold(emptyList<ObservedLocation>()) { previous, snapshot ->
                val previousByUid = previous.associateBy { it.location.uid }
                val now = SystemClock.elapsedRealtime()
                snapshot?.documents.orEmpty().mapNotNull { document ->
                    val position = document.toRoutePoint() ?: return@mapNotNull null
                    val updatedAt = document.getTimestamp("updatedAt") ?: return@mapNotNull null
                    val old = previousByUid[document.id]
                    val remaining = if (old == null) {
                        val age = (System.currentTimeMillis() - updatedAt.toDate().time)
                            .coerceIn(0L, PRESENCE_TIMEOUT_MS)
                        PRESENCE_TIMEOUT_MS - age
                    } else {
                        PRESENCE_TIMEOUT_MS
                    }
                    val expiresAt = if (old != null && old.updatedAt == updatedAt) {
                        old.expiresAt
                    } else {
                        now + remaining
                    }
                    val location = LiveLocation(
                        uid = document.id,
                        name = (document.get("name") as? String).orEmpty(),
                        position = position,
                        routeId = (document.get("routeId") as? String)
                            ?.takeIf { it.isNotBlank() && '/' !in it },
                        photoUrl = document.get("photoUrl") as? String
                    )
                    ObservedLocation(location, updatedAt, expiresAt)
                }
            }
            .combine(flow {
                while (true) {
                    emit(Unit)
                    delay(PRESENCE_CHECK_INTERVAL_MS)
                }
            }) { observations, _ ->
                val now = SystemClock.elapsedRealtime()
                observations.filter { it.expiresAt > now }.map { it.location }
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

    suspend fun refreshPresence(uid: String, routeId: String): Boolean {
        val locationRef = locations.document(uid)
        return firestore.runTransaction { transaction ->
            val current = transaction.get(locationRef)
            if (current.getString("routeId") != routeId ||
                current.getBoolean("connected") != true
            ) {
                return@runTransaction false
            }
            transaction.update(locationRef, "updatedAt", FieldValue.serverTimestamp())
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

    private data class ObservedLocation(
        val location: LiveLocation,
        val updatedAt: Timestamp,
        val expiresAt: Long
    )

    companion object {
        const val PRESENCE_REFRESH_INTERVAL_MS = 15_000L
        private const val PRESENCE_TIMEOUT_MS = 60_000L
        private const val PRESENCE_CHECK_INTERVAL_MS = 5_000L
    }
}
