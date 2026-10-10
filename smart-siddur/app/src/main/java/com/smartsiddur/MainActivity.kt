package com.smartsiddur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.smartsiddur.data.PrayerRepository
import com.smartsiddur.data.Settings
import com.smartsiddur.data.asAssetSource
import com.smartsiddur.ui.SiddurApp
import com.smartsiddur.ui.theme.SiddurTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = PrayerRepository(assets.asAssetSource())
        val settings = Settings(applicationContext)
        setContent {
            val dark = settings.isDark(isSystemInDarkTheme())
            // Keep the status/navigation bar icons readable when the app theme differs from the system's
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SiddurTheme(darkTheme = dark) {
                // The whole app is Hebrew, regardless of the device language
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SiddurApp(repository, settings)
                }
            }
        }
    }
}
