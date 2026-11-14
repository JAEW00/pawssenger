package com.pawssenger.app.analysis

import com.pawssenger.app.comfort.ComfortCalculator
import com.pawssenger.app.comfort.ComfortInputs
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.data.VehicleSample
import com.pawssenger.app.data.db.PetSafeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShakeAnalyzerTest {
    private fun loadLog(): List<VehicleSample> =
        File("src/test/assets/drive_log.csv").readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val c = line.split(",")
            VehicleSample(c[0].toLong(), c[1].toFloat(), c[2].toFloatOrNull(), c[3].toFloatOrNull(), c[4].toDoubleOrNull(), c[5].toDoubleOrNull())
        }

    @Test
    fun demoLogYieldsOneBrakeAndOneTurnEpisode() {
        val a = ShakeAnalyzer()
        loadLog().forEach { a.onSample(it) }
        assertEquals(listOf(EventType.HARD_BRAKE, EventType.SHARP_TURN), a.events.map { it.type })
        assertEquals(-4.5f, a.events[0].magnitude, 0.01f)
        assertEquals(1, a.harshCount())
        assertEquals(1, a.sharpTurnCount())
        assertEquals(3.0, a.distanceKm, 0.1)
    }

    @Test
    fun continuousThresholdSamplesCountAsSingleEvent() {
        val a = ShakeAnalyzer()
        (0 until 10).forEach { a.onSample(VehicleSample(it * 500L, 50f, -4f)) }
        a.onSample(VehicleSample(5000, 50f, 0f))
        a.onSample(VehicleSample(5500, 50f, -4f))
        assertEquals(2, a.events.size)
    }

    @Test
    fun vibrationIsZeroWhenSteadyAndRisesWhenAccelSwings() {
        val steady = ShakeAnalyzer()
        (0 until 40).forEach { steady.onSample(VehicleSample(it * 500L, 60f, 0f, 0f)) }
        assertEquals(0f, steady.vibration(), 0.001f)
        val rough = ShakeAnalyzer()
        (0 until 40).forEach { rough.onSample(VehicleSample(it * 500L, 60f, if (it % 2 == 0) 2f else -2f, 0f)) }
        assertTrue("got ${rough.vibration()}", rough.vibration() > 1.6f)
    }
}

class ComfortCalculatorTest {
    @Test
    fun calmDriveIsInTheGoodBand() {
        val c = ComfortCalculator.calculate(
            ComfortInputs(cabinTempC = 24f, noiseDb = 48, speedKph = 45f, vibration = 0.2f, petSafe = List(4) { Level.GOOD })
        )
        assertEquals(Level.GOOD, c.level)
        assertTrue(c.total >= 95)
    }

    @Test
    fun designCautionExampleLandsInTheWarnBand() {
        // 급제동 3회, 진동 보통, 소음 61dB, 창문 열림(주의)
        val c = ComfortCalculator.calculate(
            ComfortInputs(
                harshCount = 3, vibration = 3.0f, cabinTempC = 24f, noiseDb = 61, speedKph = 62f,
                petSafe = listOf(Level.GOOD, Level.GOOD, Level.WARN, Level.GOOD),
            )
        )
        assertEquals(Level.WARN, c.level)
        assertEquals(85, c.total)
    }

    @Test
    fun unfastenedHarnessWithRoughDrivingIsBad() {
        val c = ComfortCalculator.calculate(
            ComfortInputs(
                harshCount = 5, vibration = 2f, cabinTempC = 29f, noiseDb = 72, speedKph = 88f,
                petSafe = listOf(Level.GOOD, Level.BAD, Level.WARN, Level.GOOD),
            )
        )
        assertEquals(Level.BAD, c.level)
    }

    @Test
    fun scoreThresholdsMatchTheSpec() {
        assertEquals(Level.GOOD, Level.ofScore(90))
        assertEquals(Level.WARN, Level.ofScore(89))
        assertEquals(Level.WARN, Level.ofScore(71))
        assertEquals(Level.BAD, Level.ofScore(70))
    }

    @Test
    fun petSafeLevelsSurviveEncodeAndDecode() {
        val levels = listOf(Level.GOOD, Level.BAD, null, Level.WARN)
        assertEquals("GB-W", PetSafeCodec.encode(levels))
        assertEquals(levels, PetSafeCodec.decode("GB-W"))
    }
}

class AccelEstimatorTest {
    @Test
    fun longitudinalUsesAOneSecondWindowSoQuantisationDoesNotSpike() {
        val e = AccelEstimator()
        var last: Float? = null
        for (i in 0..30) last = e.longitudinal(i * 100L, 100f + (i % 2))
        assertTrue("got $last", kotlin.math.abs(last!!) < 0.5f)
    }

    @Test
    fun hardBrakingIsDetectedFromSpeedAlone() {
        val e = AccelEstimator()
        var a: Float? = null
        for (i in 0..20) a = e.longitudinal(i * 100L, 100f - i * 1.62f)
        assertEquals(-4.5f, a!!, 0.1f)
        assertEquals(null, AccelEstimator().longitudinal(0, 50f))
    }

    @Test
    fun lateralFollowsBicycleModelAndSignOfSteering() {
        val e = AccelEstimator(wheelbaseM = 2.9f)
        assertEquals(0f, e.lateral(100f, 0f), 0.001f)
        // 핸들 29° ÷ 조향비 14.5 = 앞바퀴 2° → 60km/h 에서 약 3.35 m/s²
        assertEquals(3.35f, e.lateral(60f, 29f), 0.05f)
        assertEquals(-3.35f, e.lateral(60f, -29f), 0.05f)
        assertEquals(0f, e.lateral(0f, 400f), 0.001f)
        // QA 재현: 40km/h 에서 핸들 5° 는 급회전(3.0)이 아니다
        assertTrue(e.lateral(40f, 5f) < 0.5f)
        // 같은 속도에서 핸들을 크게(60°) 돌리면 급회전 기준을 넘는다
        assertTrue(e.lateral(40f, 60f) > 3.0f)
    }
}

class CabinTempSimulatorTest {
    private val sim = com.pawssenger.app.dashboard.CabinTempSimulator

    @Test
    fun risesGraduallyTowardSetTemperatureAndStopsThere() {
        var t = 19.1f
        val tau = sim.tauSeconds(8)
        t = sim.next(t, 30f, tau, 1f)
        assertTrue("first step should be small, got $t", t > 19.1f && t < 20f)
        repeat(20) { t = sim.next(t, 30f, tau, 1f) }
        assertTrue("after ~1 tau should be well on the way, got $t", t in 25f..28f)
        repeat(300) { t = sim.next(t, 30f, tau, 1f) }
        assertEquals(30f, t, 0.001f)
    }

    @Test
    fun coolsDownTheSameWayAndWeakFanIsSlower() {
        var fast = 30f; var slow = 30f
        repeat(15) { fast = sim.next(fast, 18f, sim.tauSeconds(8), 1f); slow = sim.next(slow, 18f, sim.tauSeconds(1), 1f) }
        assertTrue(fast < slow && slow < 30f && fast > 18f)
    }
}

class NoiseEstimateTest {
    private fun level(kph: Float) = ComfortCalculator.noiseLevel(ComfortCalculator.estimateNoiseDb(42, kph))

    @Test
    fun noiseRisesWithSpeedAndCrossesLevelsAt110And150() {
        assertEquals(42, ComfortCalculator.estimateNoiseDb(42, 0f))
        assertEquals(Level.GOOD, level(60f))
        assertEquals(Level.GOOD, level(109f))
        assertEquals(Level.WARN, level(110f))
        assertEquals(Level.WARN, level(149f))
        assertEquals(Level.BAD, level(150f))
        assertTrue(ComfortCalculator.estimateNoiseDb(42, 80f) > ComfortCalculator.estimateNoiseDb(42, 40f))
    }

    @Test
    fun levelBoundariesDoNotDependOnTheBaseSetting() {
        for (base in listOf(30, 42, 50, 80)) {
            assertEquals(Level.GOOD, ComfortCalculator.noiseLevel(ComfortCalculator.estimateNoiseDb(base, 109f)))
            assertEquals(Level.WARN, ComfortCalculator.noiseLevel(ComfortCalculator.estimateNoiseDb(base, 110f)))
        }
    }
}

class ThresholdTest {
    @Test
    fun speedLevels() {
        assertEquals(Level.GOOD, ComfortCalculator.speedLevel(100f))
        assertEquals(Level.WARN, ComfortCalculator.speedLevel(101f))
        assertEquals(Level.WARN, ComfortCalculator.speedLevel(129f))
        assertEquals(Level.BAD, ComfortCalculator.speedLevel(130f))
    }

    @Test
    fun vibrationBands() {
        assertEquals(Level.GOOD, ComfortCalculator.vibrationBand(2.4f).level)
        assertEquals(Level.WARN, ComfortCalculator.vibrationBand(2.5f).level)
        assertEquals(Level.WARN, ComfortCalculator.vibrationBand(4.9f).level)
        assertEquals(Level.BAD, ComfortCalculator.vibrationBand(5f).level)
    }

    @Test
    fun harshLevelsAndScoresAgree() {
        assertEquals(Level.GOOD, ComfortCalculator.harshLevel(2))
        assertEquals(Level.WARN, ComfortCalculator.harshLevel(3))
        assertEquals(Level.WARN, ComfortCalculator.harshLevel(4))
        assertEquals(Level.BAD, ComfortCalculator.harshLevel(5))
        // 세부 점수의 단계가 타일 단계와 같아야 한다
        for (n in 0..8) {
            val score = ComfortCalculator.calculate(ComfortInputs(harshCount = n)).harsh
            assertEquals("n=$n score=$score", ComfortCalculator.harshLevel(n), Level.ofScore(score))
        }
    }

    @Test
    fun oldHarshEventsDropOutAfterTenMinutes() {
        val a = ShakeAnalyzer()
        var t = 0L
        fun feed(ms: Long, accel: Float) { t += ms; a.onSample(VehicleSample(t, 60f, accel, 0f)) }
        repeat(3) { feed(1000, -4f); feed(1000, 0f) }      // 급제동 3회
        assertEquals(3, a.harshCount())
        repeat(9 * 60) { feed(1000, 0f) }                  // 9분 뒤에도 남아 있다
        assertEquals(3, a.harshCount())
        repeat(2 * 60) { feed(1000, 0f) }                  // 11분 뒤에는 빠진다
        assertEquals(0, a.harshCount())
        assertEquals(3, a.events.size)                     // 기록 자체는 남는다
    }

    @Test
    fun oneSharpTurnDoesNotAlsoRaiseVibration() {
        val a = ShakeAnalyzer()
        (0 until 20).forEach { a.onSample(VehicleSample(it * 500L, 60f, 0f, 0f)) }
        (20 until 30).forEach { a.onSample(VehicleSample(it * 500L, 60f, 0f, 5.0f)) }   // 5초짜리 급회전
        (30 until 40).forEach { a.onSample(VehicleSample(it * 500L, 60f, 0f, 0f)) }
        assertEquals(1, a.sharpTurnCount())
        assertEquals(0f, a.vibration(), 0.001f)
        val c = ComfortCalculator.calculate(ComfortInputs(sharpTurnCount = a.sharpTurnCount(), vibration = a.vibration()))
        assertEquals(94, c.turnVibration)
    }
}

class SpeedJumpTest {
    /** 에뮬레이터에서 속도를 한 번에 바꾸는 경우: 0 → 60 → 0 으로 순간 이동 */
    @Test
    fun instantSpeedJumpsAreNotCountedAsHarshEvents() {
        val est = AccelEstimator()
        val analyzer = ShakeAnalyzer()
        var t = 0L
        fun feed(kph: Float) { t += 100; analyzer.onSample(VehicleSample(t, kph, est.longitudinal(t, kph) ?: 0f, 0f)) }
        repeat(30) { feed(0f) }
        repeat(30) { feed(60f) }   // 순간적으로 60
        repeat(30) { feed(0f) }    // 순간적으로 0
        repeat(30) { feed(100f) }
        assertEquals(emptyList<EventType>(), analyzer.events.map { it.type })
    }

    @Test
    fun realHardBrakingIsStillCountedAfterAJump() {
        val est = AccelEstimator()
        val analyzer = ShakeAnalyzer()
        var t = 0L
        fun feed(kph: Float) { t += 100; analyzer.onSample(VehicleSample(t, kph, est.longitudinal(t, kph) ?: 0f, 0f)) }
        repeat(20) { feed(0f) }
        repeat(20) { feed(100f) }                       // 값 끊김
        for (i in 1..25) feed(100f - i * 1.62f)         // 실제 급제동 (-4.5 m/s²)
        assertEquals(listOf(EventType.HARD_BRAKE), analyzer.events.map { it.type })
    }
}

class PlaceFinderTest {
    private fun place(id: String, lat: Double, lng: Double, type: com.pawssenger.app.data.PlaceType = com.pawssenger.app.data.PlaceType.ATTRACTION) =
        com.pawssenger.app.data.Place(id, "장소 $id", type, lat, lng, "주소", "", "전구역 동반가능", "전 견종 동반 가능", "목줄 착용,기타", "", "", "")
    private val finder = com.pawssenger.app.data.PlaceFinder

    @Test
    fun returnsPlacesWithinRadiusNearestFirst() {
        // 위도 0.01도 ≈ 1.11km
        val all = (1..8).map { place("p$it", 37.0 + it * 0.01, 127.0) } + place("far", 38.0, 127.0)
        val r = finder.near(all, 37.0, 127.0)
        assertEquals((1..8).map { "p$it" }, r.map { it.place.id })
        assertEquals(1.11, r[0].distanceKm, 0.01)
        assertEquals(1.11, r[0].northKm, 0.01)
        assertEquals(0.0, r[0].eastKm, 0.001)
    }

    @Test
    fun widensToNearestWhenFewInRadius() {
        val all = listOf(place("a", 37.01, 127.0), place("b", 38.0, 127.0), place("c", 39.0, 127.0))
        assertEquals(listOf("a", "b", "c"), finder.near(all, 37.0, 127.0).map { it.place.id })
    }

    @Test
    fun filtersByTypeAndBuildsTags() {
        val food = com.pawssenger.app.data.PlaceType.FOOD
        val all = (1..6).map { place("a$it", 37.0 + it * 0.01, 127.0) } + (1..6).map { place("f$it", 37.0 + it * 0.01, 127.01, food) }
        assertTrue(finder.near(all, 37.0, 127.0, food).all { it.place.type == food })
        assertEquals(listOf("전구역 동반가능", "전 견종 동반 가능", "목줄 착용"), all.first().tags)
    }
}

class SharpTurnRuleTest {
    private fun run(lat: Float, samples: Int, stepMs: Long = 100): ShakeAnalyzer {
        val a = ShakeAnalyzer()
        var t = 0L
        repeat(10) { t += stepMs; a.onSample(VehicleSample(t, 60f, 0f, 0f)) }
        repeat(samples) { t += stepMs; a.onSample(VehicleSample(t, 60f, 0f, lat)) }
        repeat(10) { t += stepMs; a.onSample(VehicleSample(t, 60f, 0f, 0f)) }
        return a
    }

    @Test
    fun gentleCurveBelowNewThresholdIsNotASharpTurn() {
        // 이전 기준(3.0)에서는 급회전이던 4.0 m/s² 가 이제는 아니다
        assertEquals(0, run(lat = 4.0f, samples = 30).sharpTurnCount())
    }

    @Test
    fun briefSpikeShorterThanHalfASecondIsIgnored() {
        assertEquals(0, run(lat = 6.0f, samples = 3).sharpTurnCount())       // 0.3초
        assertEquals(0f, run(lat = 6.0f, samples = 3).vibration(), 0.001f)   // 진동에도 넣지 않는다
    }

    @Test
    fun sustainedHardTurnCountsOnce() {
        val a = run(lat = -5.0f, samples = 20)                                // 2초, 왼쪽
        assertEquals(1, a.sharpTurnCount())
        assertEquals(-5.0f, a.events.single().magnitude, 0.01f)
    }
}
