package ro.goldenlobster.snorenudge.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "night_session", indices = [Index("startedAt")])
data class NightSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    /** Last known end time (updated every minute while running, final value on stop). */
    val endedAt: Long? = null,
    /** Null while the session is running; "interrupted" if the process died. */
    val endReason: String? = null,
    val testMode: Boolean = false,
    val settingsSummary: String = "",
    val snoreEvents: Int = 0,
    val nudges: Int = 0,
    val snoringMinutes: Int = 0,
    val gateOpenRatio: Float = 0f,
    val avgNoiseFloorDb: Float = 0f,
    val avgInferenceMs: Float = 0f,
)

@Entity(
    tableName = "snore_event",
    foreignKeys = [
        ForeignKey(
            entity = NightSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SnoreEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Long,
    val durationMs: Long,
    val maxScore: Float,
    val peakDb: Float,
)

@Entity(
    tableName = "nudge",
    foreignKeys = [
        ForeignKey(
            entity = NightSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class Nudge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Long,
    val level: Int,
)
