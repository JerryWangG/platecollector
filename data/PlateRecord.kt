package com.example.platecollector.data


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plates")
data class PlateRecord(
    @PrimaryKey val plate: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val count: Int
)