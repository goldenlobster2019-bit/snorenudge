package ro.goldenlobster.snorenudge.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    fun insert(session: NightSession): Long

    @Query(
        "UPDATE night_session SET endedAt = :endedAt, snoreEvents = :events, nudges = :nudges, " +
            "snoringMinutes = :minutes, gateOpenRatio = :gateRatio, avgNoiseFloorDb = :floorDb, " +
            "avgInferenceMs = :inferenceMs WHERE id = :id",
    )
    fun updateAggregates(
        id: Long, endedAt: Long, events: Int, nudges: Int, minutes: Int,
        gateRatio: Float, floorDb: Float, inferenceMs: Float,
    )

    @Query("UPDATE night_session SET endedAt = :endedAt, endReason = :reason WHERE id = :id")
    fun finish(id: Long, endedAt: Long, reason: String)

    @Query("UPDATE night_session SET endReason = 'interrupted' WHERE endReason IS NULL")
    fun markOrphansInterrupted()

    @Query("DELETE FROM night_session WHERE startedAt < :cutoff")
    fun deleteStartedBefore(cutoff: Long): Int

    @Query("SELECT * FROM night_session WHERE endReason IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    fun observeFinished(limit: Int): Flow<List<NightSession>>

    @Query("SELECT * FROM night_session WHERE endReason IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestFinished(): Flow<NightSession?>

    @Query("SELECT * FROM night_session WHERE endReason IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    fun latestFinished(): NightSession?

    @Query("SELECT * FROM night_session WHERE id = :id")
    fun observe(id: Long): Flow<NightSession?>

    @Query("SELECT COUNT(*) FROM night_session")
    fun count(): Int
}

@Dao
interface EventDao {
    @Insert
    fun insert(event: SnoreEventEntity): Long

    @Query("SELECT * FROM snore_event WHERE sessionId = :sessionId ORDER BY ts")
    fun observeForSession(sessionId: Long): Flow<List<SnoreEventEntity>>

    @Query("SELECT COUNT(*) FROM snore_event WHERE sessionId = :sessionId")
    fun count(sessionId: Long): Int
}

@Dao
interface NudgeDao {
    @Insert
    fun insert(nudge: Nudge): Long

    @Query("SELECT * FROM nudge WHERE sessionId = :sessionId ORDER BY ts")
    fun observeForSession(sessionId: Long): Flow<List<Nudge>>
}
