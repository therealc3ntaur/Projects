package com.smartsiddur.data

import com.smartsiddur.logic.Condition
import com.smartsiddur.logic.DayContext
import com.smartsiddur.logic.PrayerRow
import com.smartsiddur.logic.buildRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Checks the bundled prayer files (assets are on the unit-test classpath). */
class PrayerContentTest {

    private val repository = PrayerRepository { path -> javaClass.classLoader!!.getResourceAsStream(path) }
    private val catalog = repository.catalog

    private fun allPrayers() = catalog.entries.filter { it.type == "prayer" }.flatMap { entry ->
        catalog.nuschaot.map { repository.load(it.id, entry.id) }
    }

    @Test
    fun everyMenuPrayerLoads() {
        for (prayer in allPrayers()) {
            assertTrue("${prayer.id} has no sections", prayer.sections.isNotEmpty())
        }
    }

    @Test
    fun everyConditionParsesAndUsesKnownFlags() {
        for (prayer in allPrayers()) {
            val expressions = prayer.sections.flatMap { s -> listOfNotNull(s.`when`) + s.items.mapNotNull { it.`when` } }
            for (expr in expressions) {
                for (flag in Condition.parse(expr).flagNames()) {
                    assertTrue("${prayer.id}: unknown flag '$flag' in \"$expr\"", DayContext.isKnownFlag(flag))
                }
            }
        }
    }

    @Test
    fun yaalehVeyavoOnlyOnRoshChodesh() {
        val shacharit = repository.load("ashkenaz", "shacharit")
        fun hasYaalehVeyavo(date: LocalDate): Boolean {
            val day = DayContext.of(date, true, shacharit.service)
            return buildRows(shacharit, day.flags, smartMode = true, showNotes = false)
                .any { it is PrayerRow.Text && "יַעֲלֶה וְיָבֹא" in it.item.text }
        }
        assertTrue(hasYaalehVeyavo(LocalDate.of(2026, 11, 11)))
        assertFalse(hasYaalehVeyavo(LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun smartModeOffShowsEverything() {
        val prayer = repository.load("ashkenaz", "mincha")
        val flags = DayContext.of(LocalDate.of(2026, 10, 19), true, prayer.service).flags
        val all = buildRows(prayer, flags, smartMode = false, showNotes = true).count { it is PrayerRow.Text }
        assertEquals(prayer.sections.sumOf { it.items.size }, all)
        val smart = buildRows(prayer, flags, smartMode = true, showNotes = true).count { it is PrayerRow.Text }
        assertTrue(smart < all)
    }

    @Test
    fun unknownNusachFallsBackToDefault() {
        assertEquals("shacharit", repository.load("sefard", "shacharit").id)
    }
}
