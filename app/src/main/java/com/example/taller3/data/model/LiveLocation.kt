package com.example.taller3.data.model

data class LiveLocation(
    val uid: String,
    val name: String,
    val position: RoutePoint,
    val routeId: String?,
    val route: List<RoutePoint> = emptyList()
)