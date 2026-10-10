package com.smartsiddur.logic

import com.smartsiddur.data.Service
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayContextTest {

    private fun flags(y: Int, m: Int, d: Int, inIsrael: Boolean = true, service: Service? = Service.SHACHARIT) =
        DayContext.of(LocalDate.of(y, m, d), inIsrael, service).flags

    @Test
    fun plainMondayHasTachanunAndTorahReading() {
        val f = flags(2026, 10, 19) // Monday, 8 Cheshvan 5787
        assertTrue("day_2" in f)
        assertTrue("tachanun" in f)
        assertTrue("torah_reading" in f)
        assertTrue("lamenatzeach" in f)
        assertFalse("yaaleh_veyavo" in f)
        assertFalse("hallel" in f)
    }

    @Test
    fun roshChodesh() {
        val f = flags(2026, 11, 11) // 1 Kislev 5787
        assertTrue("rosh_chodesh" in f)
        assertTrue("yaaleh_veyavo" in f)
        assertTrue("hallel" in f)
        assertFalse("hallel_shalem" in f)
        assertFalse("tachanun" in f)
        assertFalse("lamenatzeach" in f)
        assertTrue("torah_reading" in f)
    }

    @Test
    fun chanukahHasAlHanisimAndFullHallel() {
        val f = flags(2026, 12, 7) // 27 Kislev
        assertTrue("chanukah" in f)
        assertTrue("al_hanisim" in f)
        assertTrue("hallel_shalem" in f)
        assertFalse("tachanun_day" in f)
    }

    @Test
    fun seasonsForRainPrayers() {
        val winter = flags(2026, 12, 15)
        assertTrue("mashiv_haruach" in winter)
        assertTrue("tal_umatar" in winter)
        val summer = flags(2027, 7, 1)
        assertTrue("morid_hatal" in summer)
        assertTrue("vesen_beracha" in summer)
        assertFalse("mashiv_haruach" in summer)
    }

    @Test
    fun talUmatarStartsLaterOutsideIsrael() {
        // 10 Cheshvan: Israel already asks for rain (from 7 Cheshvan), the diaspora only from early December
        assertTrue("tal_umatar" in flags(2026, 10, 21, inIsrael = true))
        assertFalse("tal_umatar" in flags(2026, 10, 21, inIsrael = false))
    }

    @Test
    fun fastDay() {
        val f = flags(2026, 12, 20) // 10 Tevet
        assertTrue("fast_day" in f)
        assertTrue("avinu_malkenu" in f)
        assertTrue("torah_reading" in f)
    }

    @Test
    fun noAvinuMalkenuAtMinchaOnFriday() {
        // 7 Tishrei 5787 (Aseret Yemei Teshuva) is Friday 2026-09-18
        assertTrue("avinu_malkenu" in flags(2026, 9, 18, service = Service.SHACHARIT))
        assertFalse("avinu_malkenu" in flags(2026, 9, 18, service = Service.MINCHA))
    }

    @Test
    fun motzaeiShabbatForMaariv() {
        // Maariv on Saturday night uses Sunday's date
        val f = flags(2026, 10, 18, service = Service.MAARIV) // Sunday
        assertTrue("motzaei_shabbat" in f)
        assertTrue("vihi_noam" in f)
    }

    @Test
    fun noVihiNoamBeforeYomTov() {
        // Sunday 2027-06-06 (1 Sivan); Shavuot is on Friday 6 Sivan
        val f = flags(2027, 6, 6, service = Service.MAARIV)
        assertTrue("motzaei_shabbat" in f)
        assertFalse("vihi_noam" in f)
    }

    @Test
    fun omerCount() {
        val f = flags(2027, 5, 25) // Lag BaOmer
        assertTrue("omer" in f)
        assertTrue("omer_33" in f)
    }

    @Test
    fun allFlagsAreKnown() {
        var date = LocalDate.of(2026, 9, 1)
        repeat(400) {
            for (service in listOf(null, Service.SHACHARIT, Service.MINCHA, Service.MAARIV)) {
                for (flag in DayContext.of(date, true, service).flags) {
                    assertTrue("Unknown flag $flag", DayContext.isKnownFlag(flag))
                }
            }
            date = date.plusDays(1)
        }
    }
}
