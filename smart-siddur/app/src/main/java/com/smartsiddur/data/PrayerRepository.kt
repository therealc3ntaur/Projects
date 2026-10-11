package com.smartsiddur.data

import android.content.res.AssetManager
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Opens a file under the assets root, or returns null if it doesn't exist. */
fun interface AssetSource {
    fun open(path: String): InputStream?
}

fun AssetManager.asAssetSource() = AssetSource { path ->
    try {
        open(path)
    } catch (e: FileNotFoundException) {
        null
    }
}

/**
 * Loads the catalog and prayers from assets/siddur.
 *
 * Prayers live in one folder per nusach. When a nusach doesn't (yet) have its own
 * version of a prayer, the default nusach's file is used, so a new nusach can be
 * added one prayer at a time.
 */
class PrayerRepository(private val assets: AssetSource) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Prayer>()

    val catalog: Catalog by lazy {
        val stream = assets.open("$ROOT/index.json") ?: error("Missing $ROOT/index.json")
        json.decodeFromString(Catalog.serializer(), stream.use { it.reader().readText() })
    }

    val torahReadings: TorahReadings by lazy {
        val stream = assets.open("$ROOT/torah_readings.json") ?: error("Missing $ROOT/torah_readings.json")
        json.decodeFromString(TorahReadings.serializer(), stream.use { it.reader().readText() })
    }

    fun load(nusach: String, prayerId: String): Prayer {
        val key = "$nusach/$prayerId"
        cache[key]?.let { return it }
        val stream = assets.open("$ROOT/$nusach/$prayerId.json")
            ?: assets.open("$ROOT/${catalog.defaultNusach}/$prayerId.json")
            ?: throw FileNotFoundException("No prayer '$prayerId' for nusach '$nusach'")
        val prayer = json.decodeFromString(Prayer.serializer(), stream.use { it.reader().readText() })
        cache[key] = prayer
        return prayer
    }

    companion object {
        const val ROOT = "siddur"
    }
}
