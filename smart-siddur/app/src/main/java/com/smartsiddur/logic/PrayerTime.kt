package com.smartsiddur.logic

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import com.smartsiddur.data.Rollover
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

/** Where zmanim are calculated for. [approximate] when the device location isn't known. */
data class Place(val latitude: Double, val longitude: Double, val approximate: Boolean) {
    companion object {
        /**
         * The device location when known; otherwise Jerusalem in Israel, or a rough guess from
         * the time zone's UTC offset (15° of longitude per hour) elsewhere.
         */
        fun of(latitude: Float, longitude: Float, inIsrael: Boolean, zone: ZoneId): Place = when {
            !latitude.isNaN() && !longitude.isNaN() -> Place(latitude.toDouble(), longitude.toDouble(), false)
            inIsrael -> Place(31.778, 35.235, true)
            else -> Place(40.0, TimeZone.getTimeZone(zone).rawOffset / 3_600_000.0 * 15.0, true)
        }
    }
}

/** The morning zmanim shown in Shacharit. MGA = Magen Avraham (72 minutes), GRA = Vilna Gaon. */
data class Zmanim(
    val sunrise: ZonedDateTime?,
    val sofShmaMga: ZonedDateTime?,
    val sofShmaGra: ZonedDateTime?,
    val sofTefilaMga: ZonedDateTime?,
    val sofTefilaGra: ZonedDateTime?,
    val approximate: Boolean,
)

/** Time-of-day helpers. The user can always flip "after sunset" by hand on the prayer screen. */
object PrayerTime {

    /** Whether a prayer opened at [now] belongs to the next Hebrew day. */
    fun isAfterNightfall(rollover: Rollover, now: ZonedDateTime, place: Place): Boolean =
        when (rollover) {
            Rollover.NONE -> false
            Rollover.NOON -> now.hour >= 12
            Rollover.SUNSET -> sunset(now, place)?.let { now.isAfter(it) } ?: (now.hour >= 19)
        }

    /** The civil date whose Hebrew day the prayer belongs to. */
    fun hebrewDayDate(now: ZonedDateTime, afterNightfall: Boolean): LocalDate =
        now.toLocalDate().let { if (afterNightfall) it.plusDays(1) else it }

    /** Which daily prayer fits the current time: "shacharit", "mincha" or "maariv". */
    fun suggestedPrayerId(now: ZonedDateTime, place: Place): String {
        val sunset = sunset(now, place)
        return when {
            now.hour < 4 -> "maariv"
            now.hour < 12 -> "shacharit"
            sunset == null && now.hour < 18 -> "mincha"
            sunset != null && now.isBefore(sunset) -> "mincha"
            else -> "maariv"
        }
    }

    fun sunset(now: ZonedDateTime, place: Place): ZonedDateTime? =
        calendar(now.toLocalDate(), now.zone, place).sunset.at(now.zone)

    fun zmanim(date: LocalDate, zone: ZoneId, place: Place): Zmanim {
        val c = calendar(date, zone, place)
        return Zmanim(
            sunrise = c.sunrise.at(zone),
            sofShmaMga = c.sofZmanShmaMGA.at(zone),
            sofShmaGra = c.sofZmanShmaGRA.at(zone),
            sofTefilaMga = c.sofZmanTfilaMGA.at(zone),
            sofTefilaGra = c.sofZmanTfilaGRA.at(zone),
            approximate = place.approximate,
        )
    }

    private fun calendar(date: LocalDate, zone: ZoneId, place: Place): ComplexZmanimCalendar {
        val tz = TimeZone.getTimeZone(zone)
        return ComplexZmanimCalendar(GeoLocation("", place.latitude, place.longitude, 0.0, tz)).apply {
            calendar = GregorianCalendar(tz).apply {
                clear()
                set(date.year, date.monthValue - 1, date.dayOfMonth)
            }
        }
    }

    private fun Date?.at(zone: ZoneId): ZonedDateTime? = this?.toInstant()?.atZone(zone)
}
