package com.smartsiddur.logic

import com.smartsiddur.data.PrayerRepository
import com.smartsiddur.data.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TorahReadingTest {

    private val repository = PrayerRepository { path -> javaClass.classLoader!!.getResourceAsStream(path) }
    private val data = repository.torahReadings

    private fun today(date: LocalDate, service: Service = Service.SHACHARIT, inIsrael: Boolean = true) =
        TorahReading.forDay(DayContext.of(date, inIsrael, service), data, inIsrael, service)

    @Test
    fun mondayAndThursdayReadTheComingParsha() {
        assertEquals("פרשת לך לך", today(LocalDate.of(2026, 10, 19))!!.name)
        assertNull(today(LocalDate.of(2026, 10, 20)))  // Tuesday
        assertEquals("פרשת וזאת הברכה", today(LocalDate.of(2026, 9, 24))!!.name)
    }

    @Test
    fun specialDays() {
        val rc = today(LocalDate.of(2026, 11, 11))!!
        assertEquals("ראש חודש כסלו", rc.name)
        assertEquals(listOf("כהן", "לוי", "שלישי", "רביעי"), rc.reading.aliyot.map { it.name })
        assertTrue(today(LocalDate.of(2026, 12, 7))!!.name.startsWith("חנוכה"))
        assertEquals("פורים", today(LocalDate.of(2027, 3, 23))!!.name)
        // Rosh Chodesh Tevet during Chanukah: Rosh Chodesh reading + the Chanukah day
        assertEquals(4, today(LocalDate.of(2026, 12, 10))!!.reading.aliyot.size)
    }

    @Test
    fun fastDayHasShacharitAndMinchaReadings() {
        val date = LocalDate.of(2026, 12, 20) // 10 Tevet
        assertEquals(3, today(date)!!.reading.aliyot.size)
        assertNull(today(date)!!.reading.haftarah)
        val mincha = today(date, Service.MINCHA)!!
        assertEquals("מפטיר", mincha.reading.aliyot.last().name)
        assertTrue(mincha.reading.haftarah!!.first().replace(Regex("[\\u0591-\\u05C7]"), "").startsWith("דרשו"))
    }

    @Test
    fun cholHamoedDiffersInIsraelAndAbroad() {
        val date = LocalDate.of(2026, 9, 28) // 17 Tishrei
        assertTrue(today(date, inIsrael = true)!!.name.contains("יום ב׳"))
        assertTrue(today(date, inIsrael = false)!!.name.contains("יום א׳"))
    }

    @Test
    fun fallsBackToTheParshaOutsideTheTable() {
        assertEquals(3, TorahReading.weekdayParsha(DayContext.jewishCalendar(LocalDate.of(2026, 10, 19), true)))
        val farFuture = LocalDate.of(2060, 1, 5) // a Monday
        assertTrue(today(farFuture)!!.name.startsWith("פרשת"))
    }

    @Test
    fun shacharitShowsTheReadingAndHidesEmptyHaftarah() {
        val shacharit = repository.load("ashkenaz", "shacharit")
        fun headers(date: LocalDate): List<String> {
            val day = DayContext.of(date, true, Service.SHACHARIT)
            return buildRows(TorahReading.resolve(shacharit, day, data, true), day.flags, smartMode = true)
                .filterIsInstance<PrayerRow.Header>().map { it.title }
        }
        val monday = headers(LocalDate.of(2026, 10, 19))
        assertTrue("קריאה בפרשת לך לך" in monday)
        assertFalse("הפטרה" in monday)
        assertTrue("הפטרה" in headers(LocalDate.of(2027, 8, 12))) // Tisha B'Av
    }
}
