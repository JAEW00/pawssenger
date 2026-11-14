package com.pawssenger.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReportStatsTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private fun trip(ms: Long, score: Int) = DriveSessionRecord(ms, ms, 600_000, 5.0, score, emptyList(), List(4) { null }, true, null)

    // 2026-11-14 는 토요일
    private val now = at(2026, 11, 14, 9)

    @Test
    fun weekStartsOnMondayAndMonthOnTheFirst() {
        assertEquals(at(2026, 11, 9, 0), ReportStats.start(ReportPeriod.WEEK, now))
        assertEquals(at(2026, 11, 1, 0), ReportStats.start(ReportPeriod.MONTH, now))
        assertEquals(0L, ReportStats.start(ReportPeriod.ALL, now))
        // 일요일도 그 주의 월요일로
        assertEquals(at(2026, 11, 9, 0), ReportStats.start(ReportPeriod.WEEK, at(2026, 11, 15, 23)))
        assertEquals(at(2026, 11, 2, 0), ReportStats.previousStart(ReportPeriod.WEEK, at(2026, 11, 9, 0)))
        assertEquals(at(2026, 10, 1, 0), ReportStats.previousStart(ReportPeriod.MONTH, at(2026, 11, 1, 0)))
    }

    @Test
    fun weekBucketsAreSevenDaysWithDailyAverages() {
        val all = listOf(trip(at(2026, 11, 14, 8), 90), trip(at(2026, 11, 14, 10), 70), trip(at(2026, 11, 11), 88), trip(at(2026, 11, 8), 50))
        val b = ReportStats.buckets(ReportPeriod.WEEK, all, now)
        assertEquals(listOf("월", "화", "수", "목", "금", "토", "일"), b.map { it.label })
        assertEquals(listOf(null, null, 88, null, null, 80, null), b.map { it.average })   // 지난주 일요일(11/8) 기록은 빠진다
        assertEquals(2, b[5].count)
        assertEquals(listOf(5), b.indices.filter { b[it].current })
    }

    @Test
    fun monthBucketsAreSevenDayChunksFromTheFirst() {
        val all = listOf(trip(at(2026, 11, 1), 60), trip(at(2026, 11, 7), 80), trip(at(2026, 11, 8), 90), trip(at(2026, 11, 30), 100), trip(at(2026, 10, 31), 10))
        val b = ReportStats.buckets(ReportPeriod.MONTH, all, now)
        assertEquals(listOf("1주", "2주", "3주", "4주", "5주"), b.map { it.label })       // 11월은 30일
        assertEquals(listOf(70, 90, null, null, 100), b.map { it.average })
        assertTrue(b[1].current)                                                         // 14일은 2주(8~14일)
    }

    @Test
    fun allBucketsAreMonthsUpToNow() {
        val all = listOf(trip(at(2026, 9, 20), 80), trip(at(2026, 11, 3), 90), trip(at(2026, 11, 13), 70))
        val b = ReportStats.buckets(ReportPeriod.ALL, all, now)
        assertEquals(listOf("9월", "10월", "11월"), b.map { it.label })
        assertEquals(listOf(80, null, 80), b.map { it.average })
        assertTrue(b.last().current)
    }

    @Test
    fun rangeTextShowsWhatIsBeingViewed() {
        assertEquals("11월 9일 ~ 11월 15일", ReportStats.rangeText(ReportPeriod.WEEK, now))
        assertEquals("2026년 11월", ReportStats.rangeText(ReportPeriod.MONTH, now))
    }
}
