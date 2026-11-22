package com.kmkk21.musenotibridge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE status = :status ORDER BY receivedAt DESC LIMIT :limit")
    fun observeByStatus(status: String, limit: Int = 200): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE status IN ('PENDING', 'FAILED') ORDER BY receivedAt ASC")
    suspend fun getPendingForDelivery(): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications WHERE status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE status = 'SENT' AND receivedAt >= :sinceEpochMs")
    fun observeSentCountSince(sinceEpochMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE status = 'AD_BLOCKED' AND receivedAt >= :sinceEpochMs")
    fun observeAdBlockedCountSince(sinceEpochMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE fingerprint = :fingerprint AND receivedAt >= :sinceEpochMs")
    suspend fun countRecentDuplicate(fingerprint: String, sinceEpochMs: Long): Int

    @Query("UPDATE notifications SET status = :newStatus, sentAt = :sentAt WHERE id IN (:ids)")
    suspend fun updateStatus(ids: List<Long>, newStatus: String, sentAt: Long?)

    @Query("UPDATE notifications SET status = 'PENDING', category = 'GENERAL', sentAt = NULL WHERE id = :id")
    suspend fun requeueForDelivery(id: Long)

    @Query("DELETE FROM notifications WHERE status IN ('SENT', 'AD_BLOCKED', 'IGNORED') AND receivedAt < :olderThanEpochMs")
    suspend fun pruneOld(olderThanEpochMs: Long): Int

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    // Delivery logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeliveryLog(log: DeliveryLogEntity): Long

    @Query("SELECT * FROM delivery_logs ORDER BY deliveredAt DESC LIMIT :limit")
    fun observeRecentDeliveryLogs(limit: Int = 30): Flow<List<DeliveryLogEntity>>

    @Query("DELETE FROM delivery_logs WHERE deliveredAt < :olderThanEpochMs")
    suspend fun pruneOldDeliveryLogs(olderThanEpochMs: Long): Int
}
