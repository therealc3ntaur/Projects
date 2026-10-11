package com.smartsiddur.data

import com.smartsiddur.logic.Condition
import com.smartsiddur.logic.DayContext
import com.smartsiddur.logic.PrayerRow
import com.smartsiddur.logic.BlockPosition
import com.smartsiddur.logic.buildRows
import com.smartsiddur.ui.inlineConditions
import com.smartsiddur.ui.parseMarkup
import androidx.compose.ui.graphics.Color
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
            val expressions = prayer.sections.flatMap { s ->
                listOfNotNull(s.`when`) + s.items.flatMap { listOfNotNull(it.`when`) + inlineConditions(it.text) }
            }
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
            return buildRows(shacharit, day.flags, smartMode = true)
                .any { it is PrayerRow.Text && "יַעֲלֶה וְיָבֹא" in it.item.text }
        }
        assertTrue(hasYaalehVeyavo(LocalDate.of(2026, 11, 11)))
        assertFalse(hasYaalehVeyavo(LocalDate.of(2026, 11, 12)))
    }

    private fun plain(text: String) = text.replace(Regex("<[^>]+>"), "").replace(Regex("[\u0591-\u05C7]"), "")

    private fun rows(prayerId: String, date: LocalDate): List<PrayerRow.Text> {
        val prayer = repository.load("ashkenaz", prayerId)
        val day = DayContext.of(date, true, prayer.service)
        return buildRows(prayer, day.flags, smartMode = true).filterIsInstance<PrayerRow.Text>()
    }

    @Test
    fun alHanisimOnlyOnChanukahAndPurim() {
        for (id in listOf("shacharit", "mincha", "maariv", "birkat_hamazon")) {
            fun hasAlHanisim(date: LocalDate) = rows(id, date).any { plain(it.item.text).trimStart().let { t ->
                t.startsWith("על הנסים") || t.startsWith("ועל הנסים") } }
            assertFalse("$id on a plain day", hasAlHanisim(LocalDate.of(2026, 10, 19)))
            assertTrue("$id on Chanukah", hasAlHanisim(LocalDate.of(2026, 12, 7)))
        }
    }

    @Test
    fun noHalachicNotesOrPsalmHeadings() {
        for (prayer in allPrayers()) {
            for (section in prayer.sections) {
                assertFalse("${prayer.id}: ${section.title}", section.title.startsWith("תהילים"))
                assertTrue(section.items.none { it.type == ItemType.NOTE })
            }
        }
    }

    @Test
    fun yaalehVeyavoLabelAndTextFormOneBlock() {
        val rc = rows("shacharit", LocalDate.of(2026, 11, 11))
        val label = rc.indexOfFirst { "בראש חדש ובחול המועד" in it.item.text }
        assertEquals(BlockPosition.FIRST, rc[label].block)
        assertEquals(BlockPosition.LAST, rc[label + 1].block)
    }

    @Test
    fun yaalehVeyavoShowsOnlyTodaysOption() {
        val text = rows("shacharit", LocalDate.of(2026, 11, 11))
            .first { "יַעֲלֶה" in it.item.text && "<if" in it.item.text }.item.text
        val flags = DayContext.of(LocalDate.of(2026, 11, 11), true).flags
        val shown = parseMarkup(text, Color.Black) { c ->
            if (com.smartsiddur.logic.Condition.isSatisfied(c, flags)) androidx.compose.ui.text.SpanStyle() else null
        }.text
        assertTrue(plain(shown).contains("ראש החדש הזה"))
        assertFalse(plain(shown).contains("חג המצות"))
        assertFalse(plain(shown).contains("  "))
    }

    @Test
    fun smartModeOffShowsEverything() {
        val prayer = repository.load("ashkenaz", "mincha")
        val flags = DayContext.of(LocalDate.of(2026, 10, 19), true, prayer.service).flags
        val all = buildRows(prayer, flags, smartMode = false).count { it is PrayerRow.Text }
        assertEquals(prayer.sections.sumOf { it.items.size }, all)
        val smart = buildRows(prayer, flags, smartMode = true).count { it is PrayerRow.Text }
        assertTrue(smart < all)
    }

    @Test
    fun unknownNusachFallsBackToDefault() {
        assertEquals("shacharit", repository.load("sefard", "shacharit").id)
    }
}
