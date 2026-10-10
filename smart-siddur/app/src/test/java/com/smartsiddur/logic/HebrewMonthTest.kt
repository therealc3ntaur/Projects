package com.smartsiddur.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HebrewMonthTest {

    @Test
    fun tishrei5787() {
        val month = HebrewMonth.containing(LocalDate.of(2026, 10, 1), inIsrael = true)
        assertEquals("תשרי תשפ״ז", month.title)
        assertEquals(30, month.days.size)
        assertEquals(LocalDate.of(2026, 9, 12), month.days.first().date)
        val sukkot = month.days[14]
        assertEquals("ט״ו", sukkot.hebrewDay)
        assertEquals(DayKind.YOM_TOV, sukkot.kind)
        assertTrue(sukkot.events.any { "סוכות" in it })
        assertEquals(DayKind.FAST, month.days[2].kind) // Tzom Gedaliah
    }

    @Test
    fun navigation() {
        val tishrei = HebrewMonth.containing(LocalDate.of(2026, 10, 1), inIsrael = true)
        val cheshvan = tishrei.next()
        assertEquals("חשון תשפ״ז", cheshvan.title)
        assertEquals(tishrei.title, cheshvan.previous().title)
        assertEquals("אלול תשפ״ו", tishrei.previous().title)
    }

    @Test
    fun leadingBlanksMatchWeekday() {
        val month = HebrewMonth.containing(LocalDate.of(2026, 10, 1), inIsrael = true)
        // 1 Tishrei 5787 is Shabbat
        assertEquals(6, month.leadingBlanks)
    }
}
