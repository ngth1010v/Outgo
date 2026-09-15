package app.outgo.util

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A month expressed as an Int in `yyyyMM` form (e.g. September 2026 -> 202609),
 * always derived from the *local* time zone at the moment a trade is entered.
 * Stored on every trade so monthly aggregates can be computed with cheap
 * integer comparisons instead of re-deriving the month from a timestamp.
 */
object MonthKey {

    fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val d = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return d.year * 100 + d.monthValue
    }

    fun current(zone: ZoneId = ZoneId.systemDefault()): Int = of(System.currentTimeMillis(), zone)

    /** Inclusive [monthKey - count + 1, monthKey], oldest first. */
    fun lastN(monthKey: Int, count: Int): List<Int> {
        val ym = YearMonth.of(monthKey / 100, monthKey % 100)
        return (count - 1 downTo 0).map { offset ->
            val m = ym.minusMonths(offset.toLong())
            m.year * 100 + m.monthValue
        }
    }

    fun label(monthKey: Int): String = "T${monthKey % 100}"

    fun minus(monthKey: Int, months: Long): Int {
        val ym = YearMonth.of(monthKey / 100, monthKey % 100).minusMonths(months)
        return ym.year * 100 + ym.monthValue
    }
}

/** Combines a [java.time.LocalDate] and [java.time.LocalTime] picked separately in the UI into epoch millis. */
fun toEpochMillis(date: java.time.LocalDate, time: java.time.LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long =
    ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
