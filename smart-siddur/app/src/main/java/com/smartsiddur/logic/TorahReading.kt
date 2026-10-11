package com.smartsiddur.logic

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar.Parsha
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.smartsiddur.data.Item
import com.smartsiddur.data.ItemType
import com.smartsiddur.data.Prayer
import com.smartsiddur.data.WeekdayTorah

object TorahReading {

    /** For combined parshiyot, the weekday reading is from the first of the pair. */
    private val FIRST_OF_COMBINED = mapOf(
        Parsha.VAYAKHEL_PEKUDEI to 22,
        Parsha.TAZRIA_METZORA to 27,
        Parsha.ACHREI_MOS_KEDOSHIM to 29,
        Parsha.BEHAR_BECHUKOSAI to 32,
        Parsha.CHUKAS_BALAK to 39,
        Parsha.MATOS_MASEI to 42,
        Parsha.NITZAVIM_VAYEILECH to 51,
    )

    /** The parsha (1 = Bereshit ... 54 = Vezot Haberakhah) read on a Monday/Thursday. */
    fun weekdayParsha(jc: JewishCalendar): Int? {
        // Between Yom Kippur and Simchat Torah the weekday reading is Vezot Haberakhah
        if (jc.jewishMonth == JewishDate.TISHREI && jc.jewishDayOfMonth in 10..22) return 54
        val parsha = jc.upcomingParshah
        return FIRST_OF_COMBINED[parsha] ?: parsha.ordinal.takeIf { it in 1..54 }
    }

    /** Fills sections with `"source": "weekday_torah"` with the day's reading. */
    fun resolve(prayer: Prayer, day: DayContext, torah: WeekdayTorah): Prayer {
        if (prayer.sections.none { it.source == "weekday_torah" }) return prayer
        val parsha = weekdayParsha(day.calendar)?.let { num -> torah.parshiyot.firstOrNull { it.num == num } }
        return prayer.copy(
            sections = prayer.sections.map { section ->
                if (section.source != "weekday_torah" || parsha == null) return@map section
                section.copy(
                    title = "קריאה בפרשת ${parsha.name}",
                    items = parsha.aliyot.flatMap { aliyah ->
                        listOf(
                            Item(ItemType.INSTRUCTION, "<small>${aliyah.name}:</small>"),
                            Item(ItemType.TEXT, aliyah.verses.joinToString(" ")),
                        )
                    },
                )
            },
        )
    }
}
