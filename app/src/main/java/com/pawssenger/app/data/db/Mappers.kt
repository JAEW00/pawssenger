package com.pawssenger.app.data.db

import com.pawssenger.app.analysis.DriveEvent
import com.pawssenger.app.analysis.EventType
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.data.DriveSessionRecord
import com.pawssenger.app.data.PetProfile
import com.pawssenger.app.data.PetSize

/** 펫 세이프 4항목 단계 ↔ "GGWG" (미연동은 '-') */
object PetSafeCodec {
    fun encode(levels: List<Level?>): String = levels.joinToString("") {
        when (it) { Level.GOOD -> "G"; Level.WARN -> "W"; Level.BAD -> "B"; null -> "-" }
    }

    fun decode(text: String): List<Level?> = text.map {
        when (it) { 'G' -> Level.GOOD; 'W' -> Level.WARN; 'B' -> Level.BAD; else -> null }
    }
}

fun PetEntity.toModel() = PetProfile(id, name, breed, runCatching { PetSize.valueOf(size) }.getOrDefault(PetSize.SMALL))

fun DriveSessionRecord.toEntity() = SessionEntity(
    id = id, startedAtMs = startedAtMs, durationMs = durationMs, distanceKm = distanceKm,
    comfortScore = comfortScore, petSafe = PetSafeCodec.encode(petSafe), safeStart = safeStart, routeName = routeName,
)

fun DriveSessionRecord.toEventEntities() = events.map {
    EventEntity(sessionId = id, type = it.type.name, timestampMs = it.timestampMs, magnitude = it.magnitude, lat = it.lat, lng = it.lng)
}

fun SessionWithEvents.toModel() = DriveSessionRecord(
    id = session.id,
    startedAtMs = session.startedAtMs,
    durationMs = session.durationMs,
    distanceKm = session.distanceKm,
    comfortScore = session.comfortScore,
    events = events.sortedBy { it.timestampMs }.mapNotNull { e ->
        val type = runCatching { EventType.valueOf(e.type) }.getOrNull() ?: return@mapNotNull null
        DriveEvent(type, e.timestampMs, e.magnitude, e.lat, e.lng)
    },
    petSafe = PetSafeCodec.decode(session.petSafe),
    safeStart = session.safeStart,
    routeName = session.routeName,
)
