package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.UserLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLocationDao {
    @Query("SELECT * FROM user_locations")
    fun getAllLocationsFlow(): Flow<List<UserLocationEntity>>

    @Query("SELECT * FROM user_locations")
    suspend fun getAllLocationsSync(): List<UserLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(location: UserLocationEntity)

    @Query("SELECT * FROM user_locations WHERE id = :id LIMIT 1")
    suspend fun getLocationById(id: Long): UserLocationEntity?

    @Query("SELECT * FROM user_locations WHERE name = :name LIMIT 1")
    suspend fun getLocationByName(name: String): UserLocationEntity?

    @Query("UPDATE user_locations SET name = :newName WHERE id = :id")
    suspend fun updateName(id: Long, newName: String)

    @Query("UPDATE user_locations SET name = :newName, location_type = :locationType WHERE id = :id")
    suspend fun updateNameAndType(id: Long, newName: String, locationType: String)

    @Query("UPDATE user_locations SET location_type = :locationType WHERE id = :id")
    suspend fun updateLocationType(id: Long, locationType: String)

    @Transaction
    suspend fun mergeLocations(sourceId: Long, targetId: Long) {
        if (sourceId == targetId) return
        val source = getLocationById(sourceId) ?: return
        val target = getLocationById(targetId) ?: return
        val hasTargetCoords = target.latitude != 0.0 || target.longitude != 0.0
        val merged = target.copy(
            latitude = if (hasTargetCoords) target.latitude else source.latitude,
            longitude = if (hasTargetCoords) target.longitude else source.longitude,
            address = target.address.ifBlank { source.address },
            originCount = target.originCount + source.originCount,
            destinationCount = target.destinationCount + source.destinationCount,
            lastVisitedAt = maxOf(target.lastVisitedAt, source.lastVisitedAt)
        )
        insertOrUpdate(merged)
        deleteById(sourceId)
    }

    @Query("DELETE FROM user_locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM user_locations")
    suspend fun deleteAll()
}
