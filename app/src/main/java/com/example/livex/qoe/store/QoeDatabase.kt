package com.example.livex.qoe.store

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One row per session: the summary, rewritten after every event (partial data survives a crash). */
@Entity(tableName = "qoe_sessions")
data class QoeSessionEntity(
    @PrimaryKey val sessionId: String,
    val streamId: String,
    val title: String,
    val isLive: Boolean,
    val startedAtEpochMs: Long,
    val startAtMs: Long,
    val durationMs: Long,
    val vstMs: Long?,
    val isEbvs: Boolean,
    val ebvsWaitMs: Long?,
    val stallCount: Int,
    val stallTimeMs: Long,
    val playingTimeMs: Long?,
    val avgBitrateBps: Int?,
    val bitrateSwitches: Int,
    val failures: Int,
    val lastError: String?,
    /** EndReason name; null = still open, or the app died mid-session. */
    val endReason: String?,
)

/** The raw event log: what a backend would receive, kept for drill-down and recomputation. */
@Entity(
    tableName = "qoe_events",
    foreignKeys = [
        ForeignKey(
            entity = QoeSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class QoeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    /** Milliseconds since session start. */
    val offsetMs: Long,
    val type: String,
    val detail: String,
)

@Dao
interface QoeDao {
    @Query("SELECT * FROM qoe_sessions ORDER BY startedAtEpochMs DESC")
    fun sessions(): Flow<List<QoeSessionEntity>>

    @Query("SELECT * FROM qoe_sessions WHERE sessionId = :sessionId")
    suspend fun session(sessionId: String): QoeSessionEntity?

    @Query("SELECT * FROM qoe_events WHERE sessionId = :sessionId ORDER BY id")
    suspend fun events(sessionId: String): List<QoeEventEntity>

    @Upsert
    suspend fun upsertSession(session: QoeSessionEntity)

    @Insert
    suspend fun insertEvent(event: QoeEventEntity)

    /** Summary + event in one transaction: never an event without its (updated) session row. */
    @Transaction
    suspend fun record(session: QoeSessionEntity, event: QoeEventEntity) {
        upsertSession(session)
        insertEvent(event)
    }

    /** Events go with them (ON DELETE CASCADE). */
    @Query("DELETE FROM qoe_sessions")
    suspend fun clear()
}

@Database(entities = [QoeSessionEntity::class, QoeEventEntity::class], version = 1, exportSchema = false)
abstract class LiveXDatabase : RoomDatabase() {
    abstract fun qoeDao(): QoeDao
}
