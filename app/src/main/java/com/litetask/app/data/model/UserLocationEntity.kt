package com.litetask.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep

/**
 * 物理空间映射表
 * 采用双频次计数法（Upsert），区分常在地与目的地
 */
@Keep
@Entity(tableName = "user_locations")
data class UserLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "origin_count")
    val originCount: Int = 0,

    @ColumnInfo(name = "destination_count")
    val destinationCount: Int = 0,

    @ColumnInfo(name = "last_visited_at")
    val lastVisitedAt: Long = System.currentTimeMillis()
)
