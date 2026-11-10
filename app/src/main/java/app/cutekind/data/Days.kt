package app.cutekind.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Days" are UTC days on purpose: two people in different time zones must agree on which
 * question is today's and on what counts towards the streak.
 */
object Days {
    fun epochDay(ts: Long): Long = Math.floorDiv(ts, 86_400_000L)
    fun key(ts: Long): String = LocalDate.ofEpochDay(epochDay(ts)).toString()

    fun daysTogether(anniversary: Long?, now: Long): Long? =
        anniversary?.let { epochDay(now) - it + 1 }?.takeIf { it > 0 }

    /** Days in a row (ending today or yesterday) on which both partners sent a drawing. */
    fun streak(state: AppState, now: Long): Int {
        val mine = state.sent.map { epochDay(it.ts) }.toSet()
        val theirs = state.received.map { epochDay(it.ts) }.toSet()
        val both = mine intersect theirs
        var day = epochDay(now)
        if (day !in both) day--
        var n = 0
        while (day in both) { n++; day-- }
        return n
    }

    /** Human label in the phone's own time zone, e.g. "today", "yesterday", "3 days ago". */
    fun ago(ts: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).toEpochDay() -
            LocalDate.ofInstant(Instant.ofEpochMilli(ts), zone).toEpochDay()
        return when {
            d <= 0L -> "today"
            d == 1L -> "yesterday"
            else -> "$d days ago"
        }
    }
}
