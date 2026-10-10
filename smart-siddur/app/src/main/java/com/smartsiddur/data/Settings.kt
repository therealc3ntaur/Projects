package com.smartsiddur.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.TimeZone
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** User preferences, observable from Compose and persisted in SharedPreferences. */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var fontSize by Pref(KEY_FONT_SIZE, DEFAULT_FONT_SIZE, SharedPreferences::getFloat, SharedPreferences.Editor::putFloat)
    var nusach by Pref(KEY_NUSACH, "ashkenaz", { k, d -> getString(k, d) ?: d }, SharedPreferences.Editor::putString)
    var inIsrael by Pref(KEY_IN_ISRAEL, TimeZone.getDefault().id in ISRAEL_TIME_ZONES, SharedPreferences::getBoolean, SharedPreferences.Editor::putBoolean)
    var smartMode by Pref(KEY_SMART, true, SharedPreferences::getBoolean, SharedPreferences.Editor::putBoolean)
    var showNotes by Pref(KEY_NOTES, false, SharedPreferences::getBoolean, SharedPreferences.Editor::putBoolean)

    private inner class Pref<T>(
        private val key: String,
        default: T,
        read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor,
    ) : ReadWriteProperty<Any?, T> {
        private var state by mutableStateOf(prefs.read(key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            prefs.edit().write(key, value).apply()
        }
    }

    companion object {
        const val MIN_FONT_SIZE = 14f
        const val MAX_FONT_SIZE = 56f
        const val DEFAULT_FONT_SIZE = 22f

        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_NUSACH = "nusach"
        private const val KEY_IN_ISRAEL = "in_israel"
        private const val KEY_SMART = "smart_mode"
        private const val KEY_NOTES = "show_notes"

        private val ISRAEL_TIME_ZONES = setOf("Asia/Jerusalem", "Asia/Tel_Aviv", "Israel")
    }
}
