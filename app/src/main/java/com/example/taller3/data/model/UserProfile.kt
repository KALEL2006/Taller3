package com.example.taller3.data.model

data class UserProfile(
    val name: String = "",
    val identification: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String? = null
)