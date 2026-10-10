package com.smartsiddur.logic

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.smartsiddur.logic.DayContext.Companion.formatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a day is marked in the calendar, from most to least significant. */
enum class DayKind { YOM_TOV, FAST, ROSH_CHODESH, MINOR, NONE }

data class CalendarDay(
    val date: LocalDate,
    /** E.g. "ט״ו". */
    val hebrewDay: String,
    /** E.g. "ט״ו תשרי תשפ״ז". */
    val hebrewDate: String,
    /** 1 = Sunday ... 7 = Shabbat. */
    val dayOfWeek: Int,
    val kind: DayKind,
    /** Holidays, Rosh Chodesh, parsha, omer... in Hebrew. */
    val events: List<String>,
)

/** One Hebrew month laid out for the calendar screen. */
class HebrewMonth private constructor(
    /** E.g. "תשרי תשפ״ז". */
    val title: String,
    /** E.g. "ספטמבר–אוקטובר 2026". */
    val gregorianRange: String,
    val days: List<CalendarDay>,
    private val inIsrael: Boolean,
) {
    /** Empty cells before the first day in a Sunday-first week grid. */
    val leadingBlanks: Int get() = days.first().dayOfWeek - 1

    fun next() = containing(days.last().date.plusDays(1), inIsrael)

    fun previous() = containing(days.first().date.minusDays(1), inIsrael)

    operator fun contains(date: LocalDate) = date in days.first().date..days.last().date

    companion object {
        private val hebrewLocale = Locale.forLanguageTag("he")
        private val monthName = DateTimeFormatter.ofPattern("LLLL", hebrewLocale)

        fun containing(date: LocalDate, inIsrael: Boolean): HebrewMonth {
            val jc = DayContext.jewishCalendar(date, inIsrael)
            val first = date.minusDays(jc.jewishDayOfMonth - 1L)
            val length = jc.daysInJewishMonth
            val days = (0 until length).map { day(first.plusDays(it.toLong()), inIsrael) }
            val title = "${formatter.formatMonth(jc)} ${formatter.formatHebrewNumber(jc.jewishYear)}"
            return HebrewMonth(title, gregorianRange(days.first().date, days.last().date), days, inIsrael)
        }

        fun day(date: LocalDate, inIsrael: Boolean): CalendarDay {
            val jc = DayContext.jewishCalendar(date, inIsrael)
            return CalendarDay(
                date = date,
                hebrewDay = formatter.formatHebrewNumber(jc.jewishDayOfMonth),
                hebrewDate = formatter.format(jc),
                dayOfWeek = jc.dayOfWeek,
                kind = kindOf(jc),
                events = eventsOf(jc),
            )
        }

        private fun kindOf(jc: JewishCalendar) = when {
            jc.isYomTovAssurBemelacha -> DayKind.YOM_TOV
            jc.isTaanis -> DayKind.FAST
            jc.isRoshChodesh -> DayKind.ROSH_CHODESH
            jc.yomTovIndex != -1 -> DayKind.MINOR
            else -> DayKind.NONE
        }

        private fun eventsOf(jc: JewishCalendar): List<String> = buildList {
            formatter.formatYomTov(jc).takeIf { it.isNotBlank() }?.let(::add)
            if (jc.isRoshChodesh) add(formatter.formatRoshChodesh(jc))
            if (jc.dayOfWeek == 7) {
                formatter.formatParsha(jc).takeIf { it.isNotBlank() }?.let { add("פרשת $it") }
                formatter.formatSpecialParsha(jc).takeIf { it.isNotBlank() }?.let { add("שבת $it") }
            }
            if (jc.dayOfOmer > 0) add("${formatter.formatHebrewNumber(jc.dayOfOmer)} לעומר")
        }.distinct()

        private fun gregorianRange(start: LocalDate, end: LocalDate): String = when {
            start.month == end.month -> "${monthName.format(start)} ${start.year}"
            start.year == end.year -> "${monthName.format(start)}–${monthName.format(end)} ${end.year}"
            else -> "${monthName.format(start)} ${start.year}–${monthName.format(end)} ${end.year}"
        }
    }
}
