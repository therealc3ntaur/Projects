package com.smartsiddur.logic

import com.smartsiddur.data.Item
import com.smartsiddur.data.ItemType
import com.smartsiddur.data.Prayer
import com.smartsiddur.data.Section

/** One row of the prayer reader. */
sealed interface PrayerRow {
    val key: String

    /** A section heading; a blank [title] just adds space (e.g. between psalms). */
    data class Header(
        val title: String,
        val level: Int,
        override val key: String,
        /** Index of the section in the prayer, used to expand/collapse it. */
        val sectionIndex: Int = -1,
        /** A collapsible section (e.g. Kedusha); its text rows follow only when [expanded]. */
        val collapsible: Boolean = false,
        val expanded: Boolean = false,
        /** Which zmanim to show under the heading, if any ("shema", "tefila"). */
        val zmanim: String? = null,
    ) : PrayerRow

    data class Text(
        val item: Item,
        /** Said today only because of today's date (e.g. Ya'aleh Veyavo on Rosh Chodesh). */
        val highlighted: Boolean,
        /** Not said today; only shown when smart mode is off. */
        val inactive: Boolean,
        /** Where this row sits in a run of consecutive highlighted rows, so they draw as one block. */
        val block: BlockPosition = BlockPosition.NONE,
        override val key: String,
    ) : PrayerRow
}

enum class BlockPosition {
    NONE, SINGLE, FIRST, MIDDLE, LAST;

    val isStart get() = this == SINGLE || this == FIRST
    val isEnd get() = this == SINGLE || this == LAST
}

/**
 * Flattens a prayer into display rows for the given day.
 *
 * In smart mode, sections and items whose condition is false are dropped. Otherwise everything
 * is shown and the rows that don't apply today are marked [PrayerRow.Text.inactive].
 * Halachic notes (type "note") are never shown. Collapsible sections show only their heading
 * unless their index is in [expanded].
 */
fun buildRows(
    prayer: Prayer,
    flags: Set<String>,
    smartMode: Boolean,
    expanded: Set<Int> = emptySet(),
): List<PrayerRow> {
    class Visible(val section: Section, val index: Int, val rows: List<PrayerRow.Text>)

    val visible = prayer.sections.mapIndexedNotNull { si, section ->
        val sectionActive = Condition.isSatisfied(section.`when`, flags)
        if (smartMode && !sectionActive) return@mapIndexedNotNull null
        val rows = section.items.mapIndexedNotNull { ii, item ->
            if (item.type == ItemType.NOTE) return@mapIndexedNotNull null
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
        val open = v.index in expanded
        result += PrayerRow.Header(
            title = v.section.title,
            level = v.section.level,
            key = "h${v.index}",
            sectionIndex = v.index,
            collapsible = v.section.collapsible,
            expanded = open,
            zmanim = v.section.zmanim,
        )
        if (!v.section.collapsible || open) result += markBlocks(v.rows)
    }
    return result
}

/** Marks runs of consecutive highlighted rows (e.g. a label and its text) as one block. */
private fun markBlocks(rows: List<PrayerRow.Text>): List<PrayerRow.Text> = rows.mapIndexed { i, row ->
    if (!row.highlighted) return@mapIndexed row
    val prev = rows.getOrNull(i - 1)?.highlighted == true
    val next = rows.getOrNull(i + 1)?.highlighted == true
    row.copy(
        block = when {
            prev && next -> BlockPosition.MIDDLE
            prev -> BlockPosition.LAST
            next -> BlockPosition.FIRST
            else -> BlockPosition.SINGLE
        },
    )
}
