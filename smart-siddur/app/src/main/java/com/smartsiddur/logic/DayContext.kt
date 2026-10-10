package com.smartsiddur.logic

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.kosherjava.zmanim.hebrewcalendar.TefilaRules
import com.smartsiddur.data.Service
import java.time.LocalDate

/**
 * Everything the siddur needs to know about one Hebrew day: a set of flags that the
 * "when" conditions in the prayer files are evaluated against.
 *
 * [date] is the civil date whose *daytime* falls in that Hebrew day; for a prayer said
 * after nightfall, pass the next civil date (see [PrayerTime]).
 */
class DayContext private constructor(
    val date: LocalDate,
    val calendar: JewishCalendar,
    val flags: Set<String>,
    private val service: Service?,
) {
    /** E.g. "ט״ו תשרי תשפ״ז". */
    val hebrewDate: String get() = formatter.format(calendar)

    /** Short Hebrew labels for what's special in today's prayers. */
    val highlights: List<String>
        get() = buildList {
            HIGHLIGHTS.forEach { (flag, label) -> if (flag in flags) add(label) }
            // Hallel is only part of Shacharit (and with no service, e.g. brachot, it isn't relevant)
            if ("hallel" in flags && service == Service.SHACHARIT) add(if ("hallel_shalem" in flags) "הלל שלם" else "חצי הלל")
            if (calendar.dayOfOmer > 0) add("ספירת העומר: ${formatter.formatHebrewNumber(calendar.dayOfOmer)}")
        }

    companion object {
        fun of(date: LocalDate, inIsrael: Boolean, service: Service? = null): DayContext {
            val jc = jewishCalendar(date, inIsrael)
            return DayContext(date, jc, computeFlags(jc, date, inIsrael, service), service)
        }

        fun jewishCalendar(date: LocalDate, inIsrael: Boolean) = JewishCalendar(date).apply {
            this.inIsrael = inIsrael
            isUseModernHolidays = true
        }

        val formatter = HebrewDateFormatter().apply {
            isHebrewFormat = true
            isUseGershGershayim = true
        }

        /** Flags that may appear in prayer files besides day_1..day_7 and omer_1..omer_49. */
        val KNOWN_FLAGS = setOf(
            "shabbat", "israel", "chul",
            "rosh_chodesh", "chol_hamoed", "yom_tov", "chanukah", "purim",
            "fast_day", "tisha_bav", "aseret_yemei_teshuva",
            "pesach", "shavuot", "sukkot", "shemini_atzeret", "rosh_hashana", "yom_kippur",
            "mashiv_haruach", "morid_hatal", "tal_umatar", "vesen_beracha",
            "yaaleh_veyavo", "al_hanisim", "hallel", "hallel_shalem",
            "tachanun", "tachanun_day", "avinu_malkenu", "mizmor_letoda", "lamenatzeach",
            "torah_reading", "ledavid", "omer",
            "motzaei_shabbat", "motzaei_yom_tov", "vihi_noam",
        )

        private val DYNAMIC_FLAG = Regex("""(day_[1-7]|omer_([1-9]|[1-4]\d))""")

        fun isKnownFlag(name: String) = name in KNOWN_FLAGS || DYNAMIC_FLAG.matches(name)

        private val HIGHLIGHTS = listOf(
            "rosh_chodesh" to "ראש חודש",
            "chol_hamoed" to "חול המועד",
            "chanukah" to "חנוכה",
            "purim" to "פורים",
            "fast_day" to "תענית ציבור",
            "tisha_bav" to "תשעה באב",
            "aseret_yemei_teshuva" to "עשרת ימי תשובה",
            "yaaleh_veyavo" to "יעלה ויבוא",
            "al_hanisim" to "על הניסים",
            "motzaei_shabbat" to "מוצאי שבת",
        )

        private val rules = TefilaRules()

        private fun computeFlags(
            jc: JewishCalendar,
            date: LocalDate,
            inIsrael: Boolean,
            service: Service?,
        ): Set<String> {
            val month = jc.jewishMonth
            val day = jc.jewishDayOfMonth
            val dayOfWeek = jc.dayOfWeek // 1 = Sunday ... 7 = Shabbat
            val prev = jewishCalendar(date.minusDays(1), inIsrael)

            val erevPesach = month == JewishDate.NISSAN && day == 14
            val erevYomKippur = month == JewishDate.TISHREI && day == 9
            val shushanPurim = jc.yomTovIndex == JewishCalendar.SHUSHAN_PURIM
            val fastDay = jc.isTaanis && !jc.isYomKippur && !jc.isTishaBav
            val tachanunShacharit = rules.isTachanunRecitedShacharis(jc)
            val motzaeiShabbat = prev.dayOfWeek == 7
            val motzaeiYomTov = prev.isYomTovAssurBemelacha && !jc.isAssurBemelacha

            val flags = mutableSetOf<String>()
            fun flag(name: String, on: Boolean) {
                if (on) flags += name
            }

            flags += "day_$dayOfWeek"
            flag("shabbat", dayOfWeek == 7)
            flags += if (inIsrael) "israel" else "chul"

            flag("rosh_chodesh", jc.isRoshChodesh)
            flag("chol_hamoed", jc.isCholHamoed)
            flag("yom_tov", jc.isYomTovAssurBemelacha)
            flag("chanukah", jc.isChanukah)
            flag("purim", jc.isPurim)
            flag("fast_day", fastDay)
            flag("tisha_bav", jc.isTishaBav)
            flag("aseret_yemei_teshuva", jc.isAseresYemeiTeshuva)
            flag("pesach", jc.isPesach)
            flag("shavuot", jc.isShavuos)
            flag("sukkot", jc.isSuccos)
            flag("shemini_atzeret", jc.isShminiAtzeres || jc.isSimchasTorah)
            flag("rosh_hashana", jc.isRoshHashana)
            flag("yom_kippur", jc.isYomKippur)

            flag("mashiv_haruach", rules.isMashivHaruachRecited(jc))
            flag("morid_hatal", rules.isMoridHatalRecited(jc))
            flag("tal_umatar", rules.isVeseinTalUmatarRecited(jc))
            flag("vesen_beracha", rules.isVeseinBerachaRecited(jc))
            flag("yaaleh_veyavo", rules.isYaalehVeyavoRecited(jc))
            flag("al_hanisim", rules.isAlHanissimRecited(jc))
            flag("hallel", rules.isHallelRecited(jc))
            flag("hallel_shalem", rules.isHallelShalemRecited(jc))

            flag("tachanun_day", tachanunShacharit)
            flag(
                "tachanun",
                when (service) {
                    Service.SHACHARIT -> tachanunShacharit
                    Service.MINCHA -> rules.isTachanunRecitedMincha(jc)
                    else -> false
                },
            )
            flag(
                "avinu_malkenu",
                (jc.isAseresYemeiTeshuva || fastDay) && dayOfWeek != 7 && !erevYomKippur &&
                    !(service == Service.MINCHA && dayOfWeek == 6),
            )
            flag("mizmor_letoda", !erevPesach && !jc.isPesach && !erevYomKippur)
            flag(
                "lamenatzeach",
                !(jc.isRoshChodesh || jc.isChanukah || jc.isPurim || shushanPurim || erevPesach ||
                    jc.isCholHamoed || jc.isTishaBav || erevYomKippur || jc.isYomTovAssurBemelacha),
            )
            flag(
                "torah_reading",
                dayOfWeek == 2 || dayOfWeek == 5 || jc.isRoshChodesh || jc.isChanukah ||
                    jc.isPurim || jc.isTaanis || jc.isCholHamoed,
            )
            // L'David Hashem Ori: from Rosh Chodesh Elul through Shemini Atzeret
            flag(
                "ledavid",
                month == JewishDate.ELUL || (month == JewishDate.AV && day == 30) ||
                    (month == JewishDate.TISHREI && day <= 22),
            )

            val omer = jc.dayOfOmer
            if (omer > 0) {
                flags += "omer"
                flags += "omer_$omer"
            }

            flag("motzaei_shabbat", motzaeiShabbat)
            flag("motzaei_yom_tov", motzaeiYomTov)
            // Vihi Noam is skipped when a Yom Tov falls in the coming week
            flag(
                "vihi_noam",
                motzaeiShabbat && (0L..5L).none {
                    jewishCalendar(date.plusDays(it), inIsrael).isYomTovAssurBemelacha
                },
            )
            return flags
        }
    }
}
