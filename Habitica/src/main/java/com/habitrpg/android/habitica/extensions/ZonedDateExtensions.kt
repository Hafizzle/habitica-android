package com.habitrpg.android.habitica.extensions

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.Locale


fun String?.parseToZonedDateTimeDefault(): ZonedDateTime {
    val pieces = this?.split(":") ?: listOf("0", "0")
    val hour = pieces.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = pieces.getOrNull(1)?.toIntOrNull() ?: 0
    return ZonedDateTime.now().withHour(hour).withMinute(minute).withSecond(0).withNano(0)
}

fun String.parseToZonedDateTimeUTC(): ZonedDateTime? {
    val parsed: TemporalAccessor = formatter().parseBest(
        this,
        ZonedDateTime::from,
        LocalDateTime::from
    )
    return if (parsed is ZonedDateTime) {
        parsed
    } else {
        val defaultZone: ZoneId = ZoneId.of("UTC")
        (parsed as LocalDateTime).atZone(defaultZone)
    }
}

fun Date.toZonedDateTimeLocal(): ZonedDateTime? {
    return this.toInstant().atZone(ZoneId.systemDefault())
}

/**
 * Returns full display name in default Locale (Monday, Tuesday, Wednesday, etc.)
 */
fun ZonedDateTime.dayOfWeekString(): String {
    return DayOfWeek.from(this).getDisplayName(TextStyle.FULL, Locale.getDefault())
}

fun formatter(): DateTimeFormatter =
    DateTimeFormatterBuilder().append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendPattern("['T'][' ']")
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .appendPattern("[XX]")
        .toFormatter()

fun ZonedDateTime.toEpochMilli(): Long {
    return this.toInstant().toEpochMilli()
}

fun ZonedDateTime.isSameDayAs(other: ZonedDateTime): Boolean {
    return this.truncatedTo(ChronoUnit.DAYS).isEqual(other.truncatedTo(ChronoUnit.DAYS))
}

fun ZonedDateTime.isOlderThanDays(days: Long): Boolean {
    return this.isBefore(ZonedDateTime.now().minusDays(days))
}
