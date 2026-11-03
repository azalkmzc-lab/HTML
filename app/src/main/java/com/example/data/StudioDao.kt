package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioDao {
    // Projects
    @Query("SELECT * FROM studio_projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<StudioProjectEntity>>

    @Query("SELECT * FROM studio_projects WHERE isSavedFavorite = 1 ORDER BY updatedAt DESC")
    fun observeSavedProjects(): Flow<List<StudioProjectEntity>>

    @Query("SELECT * FROM studio_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): StudioProjectEntity?

    @Query("SELECT COUNT(*) FROM studio_projects")
    suspend fun getProjectCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: StudioProjectEntity): Long

    @Update
    suspend fun updateProject(project: StudioProjectEntity)

    @Delete
    suspend fun deleteProject(project: StudioProjectEntity)

    @Query("DELETE FROM studio_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    // IPTV Channels
    @Query("SELECT * FROM iptv_channels WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    fun observeChannelsForProject(projectId: Long): Flow<List<IptvChannelEntity>>

    @Query("SELECT * FROM iptv_channels WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    suspend fun getChannelsForProjectSync(projectId: Long): List<IptvChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: IptvChannelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<IptvChannelEntity>)

    @Update
    suspend fun updateChannel(channel: IptvChannelEntity)

    @Query("DELETE FROM iptv_channels WHERE id = :channelId")
    suspend fun deleteChannelById(channelId: Long)

    @Query("DELETE FROM iptv_channels WHERE projectId = :projectId")
    suspend fun deleteAllChannelsForProject(projectId: Long)

    // Build Logs
    @Query("SELECT * FROM build_logs ORDER BY timestamp DESC")
    fun observeAllBuildLogs(): Flow<List<BuildLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildLog(log: BuildLogEntity): Long

    @Query("DELETE FROM build_logs WHERE id = :logId")
    suspend fun deleteBuildLogById(logId: Long)
}
