package com.litetask.app.data.local

import androidx.room.*
import com.litetask.app.data.model.UserLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLocationDao {
    @Query("SELECT * FROM user_locations")
    fun getAllLocationsFlow(): Flow<List<UserLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(location: UserLocationEntity)

    @Query("SELECT * FROM user_locations WHERE name = :name LIMIT 1")
    suspend fun getLocationByName(name: String): UserLocationEntity?

    @Query("DELETE FROM user_locations")
    suspend fun deleteAll()
}
