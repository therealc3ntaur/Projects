package com.smartsiddur.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A prayer as stored in assets/siddur/<nusach>/<id>.json.
 * See tools/build_content.py for how these files are produced.
 */
@Serializable
data class Prayer(
    val id: String,
    val title: String,
    /** Which daily service this is, so day rules like tachanun can be applied. */
    val service: Service? = null,
    /** When the prayer belongs to the following Hebrew day. */
    val rollover: Rollover = Rollover.NONE,
    val sections: List<Section>,
)

@Serializable
data class Section(
    val title: String,
    /** 1 = a major part of the service, 2 = a single passage within it. */
    val level: Int = 2,
    /** Condition expression; the whole section is skipped when false. */
    val `when`: String? = null,
    /** Shown closed until tapped (e.g. Kedusha, said only in the repetition). */
    val collapsible: Boolean = false,
    /** Today's times to show under the heading: "shema" or "tefila". */
    val zmanim: String? = null,
    /** Items filled in by the app for the day: "torah" (the reading) or "haftarah". */
    val source: String? = null,
    val items: List<Item> = emptyList(),
)

/**
 * assets/siddur/torah_readings.json (tools/build_torah.py): the weekday Torah readings by date.
 * Dates map to [reading index, name index] pairs for Shacharit ("s") and Mincha ("m").
 */
@Serializable
data class TorahReadings(
    val from: String,
    val to: String,
    val readings: List<Reading>,
    val names: List<String>,
    /** Monday/Thursday reading per parsha number (1 = Bereshit), for dates outside the table. */
    val parsha: Map<String, List<Int>>,
    val il: Map<String, DayReadings>,
    val chul: Map<String, DayReadings>,
)

@Serializable
data class DayReadings(val s: List<Int>? = null, val m: List<Int>? = null)

@Serializable
data class Reading(val aliyot: List<Aliyah>, val haftarah: List<String>? = null)

@Serializable
data class Aliyah(val name: String, val verses: List<String>)

@Serializable
data class Item(
    val type: ItemType = ItemType.TEXT,
    /** Hebrew text; may contain <b>, <small> and <big> tags and newlines. */
    val text: String,
    val `when`: String? = null,
)

@Serializable
enum class ItemType {
    /** Prayer text. */
    @SerialName("text") TEXT,

    /** A short rubric, e.g. "שליח ציבור:". */
    @SerialName("instruction") INSTRUCTION,

    /** A longer halachic note; can be hidden in settings. */
    @SerialName("note") NOTE,
}

@Serializable
enum class Service {
    @SerialName("shacharit") SHACHARIT,
    @SerialName("mincha") MINCHA,
    @SerialName("maariv") MAARIV,
}

@Serializable
enum class Rollover {
    /** Always said on the current Hebrew day (Shacharit, Mincha). */
    @SerialName("none") NONE,

    /** Said at night: after midday it belongs to the coming Hebrew day (Maariv). */
    @SerialName("noon") NOON,

    /** Can be said any time; after sunset it belongs to the coming Hebrew day. */
    @SerialName("sunset") SUNSET,
}

/** The table of contents: assets/siddur/index.json. */
@Serializable
data class Catalog(
    val defaultNusach: String,
    val nuschaot: List<Nusach>,
    val menu: List<MenuGroup>,
) {
    val entries: List<MenuEntry> get() = menu.flatMap { it.entries }

    fun entry(id: String): MenuEntry? = entries.firstOrNull { it.id == id }
}

@Serializable
data class Nusach(val id: String, val title: String)

@Serializable
data class MenuGroup(val title: String, val entries: List<MenuEntry>)

@Serializable
data class MenuEntry(
    /** What kind of screen opens; see Destinations in SiddurApp.kt. */
    val type: String,
    val id: String,
    val title: String,
)
