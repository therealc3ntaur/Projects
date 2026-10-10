package com.smartsiddur.logic

import com.kosherjava.zmanim.ZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import com.smartsiddur.data.Rollover
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Time-of-day helpers. The app doesn't ask for the device location; sunset is estimated
 * for Jerusalem when in Israel, and otherwise from the time zone's longitude. The user can
 * always flip "after sunset" by hand on the prayer screen.
 */
object PrayerTime {

    /** Whether a prayer opened at [now] belongs to the next Hebrew day. */
    fun isAfterNightfall(rollover: Rollover, now: ZonedDateTime, inIsrael: Boolean): Boolean =
        when (rollover) {
            Rollover.NONE -> false
            Rollover.NOON -> now.hour >= 12
            Rollover.SUNSET -> sunset(now, inIsrael)?.let { now.isAfter(it) } ?: (now.hour >= 19)
        }

    /** The civil date whose Hebrew day the prayer belongs to. */
    fun hebrewDayDate(now: ZonedDateTime, afterNightfall: Boolean): LocalDate =
        now.toLocalDate().let { if (afterNightfall) it.plusDays(1) else it }

    /** Which daily prayer fits the current time: "shacharit", "mincha" or "maariv". */
    fun suggestedPrayerId(now: ZonedDateTime, inIsrael: Boolean): String {
        val sunset = sunset(now, inIsrael)
        return when {
            now.hour < 4 -> "maariv"
            now.hour < 12 -> "shacharit"
            sunset == null && now.hour < 18 -> "mincha"
            sunset != null && now.isBefore(sunset) -> "mincha"
            else -> "maariv"
        }
    }

    fun sunset(now: ZonedDateTime, inIsrael: Boolean): ZonedDateTime? {
        val zone = TimeZone.getTimeZone(now.zone)
        val location = if (inIsrael) {
            GeoLocation("Jerusalem", 31.778, 35.235, 754.0, zone)
        } else {
            // Rough longitude from the standard UTC offset (15° per hour)
            val longitude = zone.rawOffset / 3_600_000.0 * 15.0
            GeoLocation("Estimated", 40.0, longitude, 0.0, zone)
        }
        val calendar = ZmanimCalendar(location).apply {
            calendar = GregorianCalendar(zone).apply {
                clear()
                set(now.year, now.monthValue - 1, now.dayOfMonth)
            }
        }
        return calendar.sunset?.toInstant()?.atZone(now.zone)
    }
}
