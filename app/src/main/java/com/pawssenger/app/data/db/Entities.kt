package com.pawssenger.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val breed: String,
    val size: String, // PetSize.name
)

/** 종료된 주행 세션 1건 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: Long,
    val startedAtMs: Long,
    val durationMs: Long,
    val distanceKm: Double,
    val comfortScore: Int,
    /** 이동장·하네스·창문·도어 순서로 G/W/B/- 한 글자씩 (예: "GGWG") */
    val petSafe: String,
    val safeStart: Boolean,
    val routeName: String?,
)

@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val type: String, // EventType.name
    val timestampMs: Long,
    val magnitude: Float,
    val lat: Double?,
    val lng: Double?,
)

data class SessionWithEvents(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val events: List<EventEntity>,
)

/** 펫 편안함 지수 시계열 (메인 추이 그래프 1h~12h) */
@Entity(tableName = "comfort_samples")
data class ComfortSampleEntity(
    @PrimaryKey val timestampMs: Long,
    val score: Int,
)

/** 단일 행(id = 0) 설정: 선택한 반려견, 가상 펫 센서 값 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val selectedPetId: Long?,
    val carrierLocked: Boolean,
    val harnessFastened: Boolean,
    val noiseDb: Int,
)
