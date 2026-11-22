package com.kmkk21.musenotibridge.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_logs")
data class DeliveryLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deliveredAt: Long,
    val channel: String,
    val itemCount: Int,
    val estimatedTokens: Int,
    val success: Boolean,
    val message: String
)
