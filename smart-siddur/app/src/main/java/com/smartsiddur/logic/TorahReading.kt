package com.smartsiddur.logic

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar.Parsha
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.smartsiddur.data.Item
import com.smartsiddur.data.ItemType
import com.smartsiddur.data.Prayer
import com.smartsiddur.data.Reading
import com.smartsiddur.data.Service
import com.smartsiddur.data.TorahReadings

object TorahReading {

    /** A reading with its title, e.g. "פרשת לך לך" or "ראש חודש כסלו". */
    data class Today(val reading: Reading, val name: String)

    /** The day's reading for [service], from the table (or, outside it, the Monday/Thursday parsha). */
    fun forDay(day: DayContext, data: TorahReadings, inIsrael: Boolean, service: Service?): Today? {
        val date = day.date.toString()
        val ref = if (date in data.from..data.to) {
            val entry = (if (inIsrael) data.il else data.chul)[date] ?: return null
            if (service == Service.MINCHA) entry.m else entry.s
        } else if (service == Service.SHACHARIT && "weekday_torah" in day.flags) {
            weekdayParsha(day.calendar)?.let { data.parsha[it.toString()] }
        } else {
            null
        }
        return ref?.let { (reading, name) -> Today(data.readings[reading], data.names[name]) }
    }

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

    /**
     * Fills the prayer's `"source": "torah"` section with the day's reading and its
     * `"source": "haftarah"` section with the haftarah (in place of the "@haftarah" item).
     * Sections are emptied (and so hidden) when there is nothing to read.
     */
    fun resolve(prayer: Prayer, day: DayContext, data: TorahReadings, inIsrael: Boolean): Prayer {
        if (prayer.sections.none { it.source != null }) return prayer
        val today = forDay(day, data, inIsrael, prayer.service)
        return prayer.copy(
            sections = prayer.sections.map { section ->
                when (section.source) {
                    "torah" -> section.copy(
                        title = today?.let { if (it.name.startsWith("פרשת")) "קריאה ב${it.name}" else it.name }
                            ?: section.title,
                        items = today?.reading?.aliyot.orEmpty().flatMap { aliyah ->
                            listOf(
                                Item(ItemType.INSTRUCTION, "<small>${aliyah.name}:</small>"),
                                Item(ItemType.TEXT, aliyah.verses.joinToString(" ")),
                            )
                        },
                    )
                    "haftarah" -> {
                        val haftarah = today?.reading?.haftarah
                        section.copy(
                            items = if (haftarah == null) {
                                emptyList()
                            } else {
                                section.items.map {
                                    if (it.text == "@haftarah") Item(ItemType.TEXT, haftarah.joinToString(" ")) else it
                                }
                            },
                        )
                    }
                    else -> section
                }
            },
        )
    }
}
