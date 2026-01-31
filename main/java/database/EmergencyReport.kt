package com.example.safetap.database

data class EmergencyReport(
    val id: Long,
    val userId: String,
    val type: Int,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val status: String,
    val timestamp: Long,
    val responseTime: Long?,
    val responderName: String?,
    val notes: String?
)