package com.pawssenger.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PawDao {
    // 펫 프로필
    @Query("SELECT * FROM pets ORDER BY id")
    fun pets(): Flow<List<PetEntity>>

    @Query("SELECT COUNT(*) FROM pets")
    suspend fun petCount(): Int

    @Insert
    suspend fun insertPet(pet: PetEntity): Long

    @Query("UPDATE pets SET name = :name, breed = :breed, size = :size WHERE id = :id")
    suspend fun updatePet(id: Long, name: String, breed: String, size: String)

    @Query("DELETE FROM pets WHERE id = :id")
    suspend fun deletePet(id: Long)

    // 주행 세션 + 이벤트
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC LIMIT :limit")
    fun recentSessions(limit: Int): Flow<List<SessionWithEvents>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert
    suspend fun insertEvents(events: List<EventEntity>)

    @Transaction
    suspend fun insertSessionWithEvents(session: SessionEntity, events: List<EventEntity>) {
        insertSession(session)
        insertEvents(events)
    }

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    /** 주행 1건 삭제 (이벤트는 외래키 CASCADE 로 함께 지워진다) */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // 편안함 지수 시계열
    @Query("SELECT * FROM comfort_samples WHERE timestampMs >= :sinceMs ORDER BY timestampMs")
    fun comfortSamplesSince(sinceMs: Long): Flow<List<ComfortSampleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComfortSample(sample: ComfortSampleEntity)

    @Query("DELETE FROM comfort_samples WHERE timestampMs < :beforeMs")
    suspend fun pruneComfortSamples(beforeMs: Long)

    // 설정
    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun settings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SettingsEntity)
}

@Database(
    entities = [PetEntity::class, SessionEntity::class, EventEntity::class, ComfortSampleEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PawDatabase : RoomDatabase() {
    abstract fun dao(): PawDao

    companion object {
        fun create(context: Context): PawDatabase =
            Room.databaseBuilder(context.applicationContext, PawDatabase::class.java, "pawssenger.db")
                // 개발 단계: 스키마가 바뀌면 기존 데이터를 지우고 새로 만든다. 출시 전에 Migration 으로 교체할 것.
                .fallbackToDestructiveMigration()
                .build()
    }
}
