package com.pawssenger.app.data

import android.content.Context
import android.util.Log
import com.pawssenger.app.analysis.DriveEvent
import com.pawssenger.app.analysis.EventType
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.data.db.ComfortSampleEntity
import com.pawssenger.app.data.db.PawDao
import com.pawssenger.app.data.db.PetEntity
import com.pawssenger.app.data.db.SettingsEntity
import com.pawssenger.app.data.db.toEntity
import com.pawssenger.app.data.db.toEventEntities
import com.pawssenger.app.data.db.toModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 앱 상태 저장소 (Room DB).
 *  - 펫 프로필 · 주행 세션(+이벤트) · 편안함 지수 시계열: DB 를 그대로 관찰한다.
 *  - 설정(선택한 반려견, 가상 펫 센서): 화면 반응성을 위해 메모리 값을 먼저 바꾸고 DB 에는 뒤따라 저장한다.
 */
class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dao: PawDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val pets: StateFlow<List<PetProfile>> = dao.pets()
        .map { list -> list.map { it.toModel() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<DriveSessionRecord>> = dao.recentSessions(RECENT_SESSION_LIMIT)
        .map { list -> list.map { it.toModel() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _selectedPetId = MutableStateFlow<Long?>(null)
    val selectedPetId: StateFlow<Long?> = _selectedPetId.asStateFlow()

    private val prefs = context.getSharedPreferences("pawssenger", Context.MODE_PRIVATE)
    private val _cabinTempDemo = MutableStateFlow(prefs.getBoolean(KEY_CABIN_TEMP_DEMO, true))
    /** 실내 온도 모드: true = 시연(희망 온도를 서서히 따라감), false = 차량이 주는 실제 값 */
    val cabinTempDemo: StateFlow<Boolean> = _cabinTempDemo.asStateFlow()

    fun setCabinTempDemo(on: Boolean) {
        _cabinTempDemo.value = on
        prefs.edit().putBoolean(KEY_CABIN_TEMP_DEMO, on).apply()
    }

    private val _sensors = MutableStateFlow(PetSensorState())
    /** 가상 펫 센서. 실제 센서(BLE 등)가 생기면 이 흐름의 공급원만 바꾸면 된다. */
    val petSensors: StateFlow<PetSensorState> = _sensors.asStateFlow()

    /** 앱에 내장한 반려동물 동반 장소 (assets/places.json). 처음 쓸 때 한 번 읽는다. */
    private val allPlaces: List<Place> by lazy { loadPlaces() }

    /** 기준점([lat], [lng]) 주변의 반려동물 동반 장소를 가까운 순으로 돌려준다. 인터넷이 필요 없다. */
    fun placesNear(lat: Double, lng: Double, type: PlaceType? = null): List<NearbyPlace> =
        PlaceFinder.near(allPlaces, lat, lng, type)

    /** 저장된 설정을 읽어 온 뒤에만 DB 쓰기를 시작한다 (초기 기본값으로 덮어쓰지 않도록). */
    private val loadJob: Job = scope.launch {
        val saved = withContext(io) { dao.settings() }
        if (saved == null) {
            // 첫 실행: 예시 프로필 1건
            _selectedPetId.value = withContext(io) {
                if (dao.petCount() == 0) dao.insertPet(PetEntity(name = "보리", breed = "말티즈", size = PetSize.SMALL.name)) else null
            }
        } else {
            _selectedPetId.value = saved.selectedPetId
            _sensors.value = PetSensorState(saved.carrierLocked, saved.harnessFastened)   // 정차 시 소음은 기본값(45dB) 고정
        }
        write("prune") { dao.pruneComfortSamples(System.currentTimeMillis() - SAMPLE_RETENTION_MS) }
    }

    init {
        scope.launch {
            loadJob.join()
            combine(_selectedPetId, _sensors) { petId, s ->
                SettingsEntity(selectedPetId = petId, carrierLocked = s.carrierLocked, harnessFastened = s.harnessFastened, noiseDb = s.noiseDb)
            }.conflate().collect { entity -> write("settings") { dao.upsertSettings(entity) } }
        }
    }

    // ── 프로필 ──
    fun selectedPet(): PetProfile? = pets.value.firstOrNull { it.id == _selectedPetId.value } ?: pets.value.firstOrNull()

    fun selectPet(id: Long) { _selectedPetId.value = id }

    fun addPet(name: String, breed: String, size: PetSize) {
        scope.launch { write("pet") { _selectedPetId.value = dao.insertPet(PetEntity(name = name, breed = breed, size = size.name)) } }
    }

    fun updatePet(id: Long, name: String, breed: String, size: PetSize) {
        scope.launch { write("pet") { dao.updatePet(id, name, breed, size.name) } }
    }

    fun deletePet(id: Long) {
        if (_selectedPetId.value == id) _selectedPetId.value = pets.value.firstOrNull { it.id != id }?.id
        scope.launch { write("pet") { dao.deletePet(id) } }
    }

    /** 주행이 끝나면 이동장·하네스 확인을 지운다 (다음 출발 전에 다시 확인). */
    fun resetSafetyChecks() = _sensors.update { it.copy(carrierLocked = false, harnessFastened = false) }

    // ── 보호자 확인 항목 · 가상 소음 ──
    fun updateSensors(transform: (PetSensorState) -> PetSensorState) = _sensors.update(transform)

    // ── 주행 기록 ──
    fun addSession(record: DriveSessionRecord) {
        scope.launch { write("session") { dao.insertSessionWithEvents(record.toEntity(), record.toEventEntities()) } }
    }

    fun clearSessions() {
        scope.launch { write("session") { dao.clearSessions() } }
    }

    fun deleteSession(id: Long) {
        scope.launch { write("session") { dao.deleteSession(id) } }
    }

    /** 이동 리포트 시연용 예시 주행 6건 (디자인 시안의 값). */
    fun seedDemoSessions() {
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        fun rec(daysAgo: Int, min: Int, km: Double, score: Int, safe: String, route: String, brakes: Int, turns: Int): DriveSessionRecord {
            val start = now - daysAgo * day - min * 60_000L
            val events = List(brakes) { DriveEvent(EventType.HARD_BRAKE, start + (it + 1) * 60_000L, -4.2f) } +
                List(turns) { DriveEvent(EventType.SHARP_TURN, start + (it + 2) * 90_000L, 3.4f) }
            val levels = safe.map { when (it) { 'G' -> Level.GOOD; 'W' -> Level.WARN; else -> Level.BAD } }
            return DriveSessionRecord(start, start, min * 60_000L, km, score, events, levels, levels.all { it == Level.GOOD }, route)
        }
        listOf(
            rec(0, 38, 21.0, 92, "GGGG", "집 → 포레스트 독 카페", 1, 1),
            rec(1, 24, 12.0, 92, "GGGG", "집 → 동물병원", 0, 1),
            rec(3, 52, 38.0, 88, "GGWG", "집 → 반려견 놀이공원", 2, 1),
            rec(4, 47, 35.0, 68, "GBWG", "공원 → 집", 5, 3),
            rec(6, 65, 54.0, 94, "GGGG", "집 → 펫 스테이 호텔", 0, 0),
            rec(7, 31, 17.0, 86, "GGGG", "집 → 반려견 미용실", 2, 2),
        ).forEach(::addSession)
    }

    // ── 편안함 지수 시계열 ──
    fun comfortSamplesSince(sinceMs: Long): Flow<List<Pair<Long, Int>>> =
        dao.comfortSamplesSince(sinceMs).map { list -> list.map { it.timestampMs to it.score } }

    fun addComfortSample(timestampMs: Long, score: Int) {
        scope.launch { write("sample") { dao.insertComfortSample(ComfortSampleEntity(timestampMs, score)) } }
    }

    private suspend fun write(what: String, block: suspend () -> Unit) {
        try {
            withContext(io) { block() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "DB write failed: $what", e)
        }
    }

    private fun loadPlaces(): List<Place> = try {
        val text = context.assets.open("places.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val type = runCatching { PlaceType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
            Place(
                id = o.getString("id"), name = o.getString("name"), type = type,
                lat = o.getDouble("lat"), lng = o.getDouble("lng"),
                address = o.optString("address"), tel = o.optString("tel"),
                petArea = o.optString("petArea"), petWho = o.optString("petWho"), petNeed = o.optString("petNeed"),
                petNote = o.optString("petNote"), petFacility = o.optString("petFacility"), petSupply = o.optString("petSupply"),
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "places.json 을 읽지 못했어요", e)
        emptyList()
    }

    companion object {
        private const val TAG = "AppRepository"
        private const val KEY_CABIN_TEMP_DEMO = "cabin_temp_demo"
        const val RECENT_SESSION_LIMIT = 200
        private const val SAMPLE_RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
