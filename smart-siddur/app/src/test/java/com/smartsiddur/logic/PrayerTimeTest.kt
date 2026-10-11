package com.smartsiddur.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PrayerTimeTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    @Test
    fun morningZmanimAreInOrder() {
        val z = PrayerTime.zmanim(LocalDate.of(2026, 10, 19), jerusalem, Place(31.778, 35.235, false))
        val times = listOf(z.sunrise, z.sofShmaMga, z.sofShmaGra, z.sofTefilaMga, z.sofTefilaGra).map { it!! }
        assertEquals(times.sortedBy { it.toInstant() }, times)
        // Sunrise in Jerusalem in late October is a bit after 6 (Israel is on summer time until the 25th)
        assertEquals(6, z.sunrise!!.hour)
        assertFalse(z.approximate)
    }

    @Test
    fun fallsBackToJerusalemInIsrael() {
        val place = Place.of(Float.NaN, Float.NaN, inIsrael = true, zone = jerusalem)
        assertTrue(place.approximate)
        assertEquals(31.778, place.latitude, 0.001)
    }
}
