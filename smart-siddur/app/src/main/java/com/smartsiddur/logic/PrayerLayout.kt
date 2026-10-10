package com.smartsiddur.logic

import com.smartsiddur.data.Item
import com.smartsiddur.data.ItemType
import com.smartsiddur.data.Prayer
import com.smartsiddur.data.Section

/** One row of the prayer reader. */
sealed interface PrayerRow {
    val key: String

    data class Header(val title: String, val level: Int, override val key: String) : PrayerRow

    data class Text(
        val item: Item,
        /** Said today only because of today's date (e.g. Ya'aleh Veyavo on Rosh Chodesh). */
        val highlighted: Boolean,
        /** Not said today; only shown when smart mode is off. */
        val inactive: Boolean,
        override val key: String,
    ) : PrayerRow
}

/**
 * Flattens a prayer into display rows for the given day.
 *
 * In smart mode, sections and items whose condition is false are dropped. Otherwise everything
 * is shown and the rows that don't apply today are marked [PrayerRow.Text.inactive].
 */
fun buildRows(
    prayer: Prayer,
    flags: Set<String>,
    smartMode: Boolean,
    showNotes: Boolean,
): List<PrayerRow> {
    class Visible(val section: Section, val index: Int, val rows: List<PrayerRow.Text>)

    val visible = prayer.sections.mapIndexedNotNull { si, section ->
        val sectionActive = Condition.isSatisfied(section.`when`, flags)
        if (smartMode && !sectionActive) return@mapIndexedNotNull null
        val rows = section.items.mapIndexedNotNull { ii, item ->
            if (item.type == ItemType.NOTE && !showNotes) return@mapIndexedNotNull null
            val active = sectionActive && Condition.isSatisfied(item.`when`, flags)
            if (smartMode && !active) return@mapIndexedNotNull null
            PrayerRow.Text(
                item = item,
                highlighted = active && item.`when` != null,
                inactive = !active,
                key = "$si.$ii",
            )
        }
        if (section.level > 1 && rows.isEmpty()) null else Visible(section, si, rows)
    }

    val result = mutableListOf<PrayerRow>()
    visible.forEachIndexed { i, v ->
        val next = visible.getOrNull(i + 1)
        val emptyPart = v.section.level == 1 && v.rows.isEmpty() && (next == null || next.section.level == 1)
        if (emptyPart) return@forEachIndexed
        result += PrayerRow.Header(v.section.title, v.section.level, "h${v.index}")
        result += v.rows
    }
    return result
}
