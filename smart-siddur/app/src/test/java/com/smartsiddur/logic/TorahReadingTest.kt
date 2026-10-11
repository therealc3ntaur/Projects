package com.smartsiddur.logic

import com.smartsiddur.data.PrayerRepository
import com.smartsiddur.data.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TorahReadingTest {

    private val repository = PrayerRepository { path -> javaClass.classLoader!!.getResourceAsStream(path) }

    private fun parsha(date: LocalDate) = TorahReading.weekdayParsha(DayContext.jewishCalendar(date, true))

    @Test
    fun readsTheComingShabbatsParsha() {
        assertEquals(2, parsha(LocalDate.of(2026, 10, 12)))  // Monday after Shabbat Bereshit: Noach
        assertEquals(3, parsha(LocalDate.of(2026, 10, 19)))  // Lech Lecha
    }

    @Test
    fun vezotHaberakhahBetweenYomKippurAndSimchatTorah() {
        assertEquals(54, parsha(LocalDate.of(2026, 9, 24)))  // Thursday 13 Tishrei
    }

    @Test
    fun allParshiyotHaveThreeAliyotOfAtLeastTenVerses() {
        val torah = repository.weekdayTorah
        assertEquals((1..54).toList(), torah.parshiyot.map { it.num })
        for (p in torah.parshiyot) {
            assertEquals(p.name, 3, p.aliyot.size)
            assertTrue(p.name, p.aliyot.sumOf { it.verses.size } >= 10)
        }
    }

    @Test
    fun shacharitShowsTheReadingOnlyOnPlainMondaysAndThursdays() {
        val shacharit = repository.load("ashkenaz", "shacharit")
        fun reading(date: LocalDate): String? {
            val day = DayContext.of(date, true, Service.SHACHARIT)
            val rows = buildRows(TorahReading.resolve(shacharit, day, repository.weekdayTorah), day.flags, smartMode = true)
            return rows.filterIsInstance<PrayerRow.Header>().firstOrNull { it.title.startsWith("קריאה בפרשת") }?.title
        }
        assertEquals("קריאה בפרשת לך לך", reading(LocalDate.of(2026, 10, 19)))
        assertEquals(null, reading(LocalDate.of(2026, 10, 20)))  // Tuesday
        assertEquals(null, reading(LocalDate.of(2026, 10, 12)))  // Monday, Rosh Chodesh
        assertFalse("weekday_torah" in DayContext.of(LocalDate.of(2026, 12, 7), true).flags) // Chanukah Monday
    }
}
