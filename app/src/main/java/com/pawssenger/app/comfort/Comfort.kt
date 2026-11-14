package com.pawssenger.app.comfort

import kotlin.math.roundToInt

/** 모든 지표·점수가 공유하는 3단계 */
enum class Level(val label: String) {
    GOOD("안정"), WARN("주의"), BAD("위험");

    companion object {
        /** 펫 편안함 지수 → 단계 (안정 90↑ / 주의 71~89 / 위험 70↓) */
        fun ofScore(score: Int): Level = when {
            score >= 90 -> GOOD
            score >= 71 -> WARN
            else -> BAD
        }

        fun worst(levels: Iterable<Level>): Level = levels.maxByOrNull { it.ordinal } ?: GOOD
    }
}

enum class VibrationBand(val label: String, val level: Level) {
    LOW("낮음", Level.GOOD), MID("보통", Level.WARN), HIGH("높음", Level.BAD)
}

data class ComfortInputs(
    /** 최근 10분 동안의 급가속 + 급제동 횟수 */
    val harshCount: Int = 0,
    val sharpTurnCount: Int = 0,
    /** 최근 구간 가속도 흔들림(표준편차, m/s²). 모르면 null */
    val vibration: Float? = null,
    val cabinTempC: Float? = null,
    val noiseDb: Int? = null,
    val speedKph: Float = 0f,
    /** 펫 세이프 항목들의 단계 (주의·위험이면 감점) */
    val petSafe: List<Level> = emptyList(),
)

data class ComfortBreakdown(
    val harsh: Int,
    val turnVibration: Int,
    val cabin: Int,
    val stability: Int,
    val total: Int,
) {
    val level: Level get() = Level.ofScore(total)
}

/**
 * 펫 편안함 지수 계산.
 *
 * ※ 실제 반려견의 스트레스·건강을 측정한 값이 아니다. 주행·실내 환경이 얼마나 부드러운지를 나타내는 참고 지표이며,
 *    계산식과 가중치는 확정 전이다. 바꿀 때는 이 파일만 고치면 된다.
 */
object ComfortCalculator {
    // 지표별 단계 기준 ─ 화면의 칩 색과 점수가 같은 기준을 쓴다
    fun tempLevel(c: Float?): Level = when {
        c == null -> Level.GOOD
        c in 18f..26f -> Level.GOOD
        c in 15f..30f -> Level.WARN
        else -> Level.BAD
    }

    /**
     * 실내 소음 추정(dB). 소음 센서가 없어 속도로 만든 값이다 — 실제 측정값이 아니다.
     * 정차 시 [baseDb] 에서 시작해 시속 110km 에서 주의 기준(55dB), 150km 에서 위험 기준(70dB)에 닿는다.
     */
    fun estimateNoiseDb(baseDb: Int, speedKph: Float): Int {
        val base = baseDb.coerceIn(NOISE_BASE_MIN, NOISE_BASE_MAX).toFloat()
        val v = speedKph.coerceAtLeast(0f)
        val db = if (v <= NOISE_WARN_KPH) base + (NOISE_WARN_DB - base) * (v / NOISE_WARN_KPH)
        else NOISE_WARN_DB + (NOISE_BAD_DB - NOISE_WARN_DB) * ((v - NOISE_WARN_KPH) / (NOISE_BAD_KPH - NOISE_WARN_KPH))
        // 반올림하면 110km 직전에 55 로 올라가 버리므로 내림한다 (기준 속도에 닿아야 단계가 바뀐다).
        return db.toInt().coerceAtMost(NOISE_MAX_DB)
    }

    private const val NOISE_BASE_MIN = 30
    private const val NOISE_BASE_MAX = 50
    private const val NOISE_WARN_KPH = 110f
    private const val NOISE_BAD_KPH = 150f
    private const val NOISE_WARN_DB = 55f
    private const val NOISE_BAD_DB = 70f
    private const val NOISE_MAX_DB = 90

    fun noiseLevel(db: Int?): Level = when {
        db == null || db < 55 -> Level.GOOD
        db < 70 -> Level.WARN
        else -> Level.BAD
    }

    /** 시속 100km 까지 안정, 130km 미만 주의, 130km 이상 위험 */
    fun speedLevel(kph: Float): Level = when {
        kph <= 100f -> Level.GOOD
        kph < 130f -> Level.WARN
        else -> Level.BAD
    }

    /** 최근 10분 동안의 급가속·급제동 횟수: 2회까지 안정, 3~4회 주의, 5회 이상 위험 */
    fun harshLevel(count: Int): Level = when {
        count <= 2 -> Level.GOOD
        count <= 4 -> Level.WARN
        else -> Level.BAD
    }

    /** 주행 진동(m/s²): 2.5 미만 낮음, 5 미만 보통, 5 이상 높음 */
    fun vibrationBand(v: Float?): VibrationBand = when {
        v == null || v < 2.5f -> VibrationBand.LOW
        v < 5f -> VibrationBand.MID
        else -> VibrationBand.HIGH
    }

    private fun envScore(l: Level) = when (l) { Level.GOOD -> 96; Level.WARN -> 80; Level.BAD -> 55 }

    fun calculate(i: ComfortInputs): ComfortBreakdown {
        // 타일 단계와 점수 구간이 어긋나지 않게 맞춘다: 2회까지 90점대(안정), 3~4회 70~80점대(주의), 5회부터 70점 이하(위험)
        val n = i.harshCount
        val harsh = when {
            n <= 2 -> 100 - 4 * n
            n <= 4 -> 92 - 7 * (n - 2)
            else -> 78 - 10 * (n - 4)
        }.coerceIn(40, 100)
        val vibPenalty = when (vibrationBand(i.vibration)) { VibrationBand.LOW -> 0; VibrationBand.MID -> 10; VibrationBand.HIGH -> 25 }
        val turnVib = (100 - 6 * i.sharpTurnCount - vibPenalty).coerceIn(40, 100)
        val cabin = ((envScore(tempLevel(i.cabinTempC)) + envScore(noiseLevel(i.noiseDb))) / 2f).roundToInt()
        // 시속 100km 까지는 감점 없음, 그 위로 1km 마다 0.5점 (130km 에서 85점)
        val stability = (100 - ((i.speedKph - 100f).coerceAtLeast(0f) * 0.5f)).roundToInt().coerceIn(50, 100)

        val weighted = W_HARSH * harsh + W_TURN_VIB * turnVib + W_CABIN * cabin + W_STABILITY * stability
        val safePenalty = i.petSafe.sumOf { when (it) { Level.GOOD -> 0; Level.WARN -> SAFE_WARN_PENALTY; Level.BAD -> SAFE_BAD_PENALTY }.toInt() }
        val total = (weighted - safePenalty).roundToInt().coerceIn(0, 100)
        return ComfortBreakdown(harsh, turnVib, cabin, stability, total)
    }

    private const val W_HARSH = 0.30f
    private const val W_TURN_VIB = 0.25f
    private const val W_CABIN = 0.20f
    private const val W_STABILITY = 0.25f
    private const val SAFE_WARN_PENALTY = 6
    private const val SAFE_BAD_PENALTY = 18
}
