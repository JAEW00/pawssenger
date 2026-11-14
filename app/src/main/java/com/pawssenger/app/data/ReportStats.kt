package com.pawssenger.app.data

import java.util.Calendar
import kotlin.math.roundToInt

/** 이동 리포트의 기간. 그래프 묶음 단위와 짝을 이룬다: 이번 주 → 요일별, 이번 달 → 주별, 전체 → 월별. */
enum class ReportPeriod(val label: String, val bucketLabel: String, val previousLabel: String?) {
    WEEK("이번 주", "요일별", "지난주"),
    MONTH("이번 달", "주별", "지난달"),
    ALL("전체", "월별", null),
}

/** 그래프의 막대 하나. 그 구간에 주행이 없으면 [average] 는 null. */
data class ReportBucket(val label: String, val average: Int?, val count: Int, val current: Boolean)

/** 이동 리포트의 기간 계산과 그래프 묶음. 시계에 의존하지 않도록 [now] 를 받는다. */
object ReportStats {
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")
    private const val MAX_MONTHS = 6

    private fun dayStart(ms: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /** 기간의 시작 시각. 이번 주는 월요일 0시, 이번 달은 1일 0시, 전체는 0. */
    fun start(period: ReportPeriod, now: Long): Long = when (period) {
        ReportPeriod.WEEK -> dayStart(now).apply {
            val fromMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7 // 월=0 … 일=6
            add(Calendar.DAY_OF_YEAR, -fromMonday)
        }.timeInMillis
        ReportPeriod.MONTH -> dayStart(now).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        ReportPeriod.ALL -> 0L
    }

    /** 직전 같은 단위 기간의 시작 (이번 주 → 지난주 월요일, 이번 달 → 지난달 1일) */
    fun previousStart(period: ReportPeriod, start: Long): Long = Calendar.getInstance().apply {
        timeInMillis = start
        if (period == ReportPeriod.WEEK) add(Calendar.DAY_OF_YEAR, -7) else add(Calendar.MONTH, -1)
    }.timeInMillis

    private fun bucket(label: String, sessions: List<DriveSessionRecord>, current: Boolean) = ReportBucket(
        label, if (sessions.isEmpty()) null else sessions.map { it.comfortScore }.average().roundToInt(), sessions.size, current,
    )

    /**
     * 그래프 막대 목록.
     *  - 이번 주: 월~일 7칸
     *  - 이번 달: 1일부터 7일씩 묶은 주 (1주 = 1~7일 …)
     *  - 전체: 첫 기록이 있는 달부터 이번 달까지, 최근 [MAX_MONTHS] 개월
     */
    fun buckets(period: ReportPeriod, all: List<DriveSessionRecord>, now: Long): List<ReportBucket> {
        val start = start(period, now)
        return when (period) {
            ReportPeriod.WEEK -> (0 until 7).map { i ->
                val from = dayStart(start).apply { add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
                val to = dayStart(start).apply { add(Calendar.DAY_OF_YEAR, i + 1) }.timeInMillis
                bucket(WEEKDAYS[i], all.filter { it.startedAtMs in from until to }, now in from until to)
            }
            ReportPeriod.MONTH -> {
                val cal = dayStart(start)
                val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                (0 until (days + 6) / 7).map { w ->
                    val from = dayStart(start).apply { add(Calendar.DAY_OF_YEAR, w * 7) }.timeInMillis
                    val to = dayStart(start).apply { add(Calendar.DAY_OF_YEAR, minOf(days, (w + 1) * 7)) }.timeInMillis
                    bucket("${w + 1}주", all.filter { it.startedAtMs in from until to }, now in from until to)
                }
            }
            ReportPeriod.ALL -> {
                fun monthKey(ms: Long) = Calendar.getInstance().run { timeInMillis = ms; get(Calendar.YEAR) * 12 + get(Calendar.MONTH) }
                val nowKey = monthKey(now)
                val firstKey = all.minOfOrNull { monthKey(it.startedAtMs) } ?: nowKey
                (maxOf(firstKey, nowKey - MAX_MONTHS + 1)..nowKey).map { key ->
                    bucket("${key % 12 + 1}월", all.filter { monthKey(it.startedAtMs) == key }, key == nowKey)
                }
            }
        }
    }

    /** 화면에 보여 줄 기간 범위 문구 */
    fun rangeText(period: ReportPeriod, now: Long): String {
        val s = Calendar.getInstance().apply { timeInMillis = start(period, now) }
        return when (period) {
            ReportPeriod.WEEK -> {
                val e = Calendar.getInstance().apply { timeInMillis = s.timeInMillis + 6 * DAY_MS }
                "${s.get(Calendar.MONTH) + 1}월 ${s.get(Calendar.DAY_OF_MONTH)}일 ~ ${e.get(Calendar.MONTH) + 1}월 ${e.get(Calendar.DAY_OF_MONTH)}일"
            }
            ReportPeriod.MONTH -> "${s.get(Calendar.YEAR)}년 ${s.get(Calendar.MONTH) + 1}월"
            ReportPeriod.ALL -> "모든 기록"
        }
    }
}
