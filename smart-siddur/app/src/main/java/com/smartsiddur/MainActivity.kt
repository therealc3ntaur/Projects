package com.smartsiddur

import android.os.Bundle
import androidx.activity.ComponentActivity
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
            SiddurTheme {
                // The whole app is Hebrew, regardless of the device language
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SiddurApp(repository, settings)
                }
            }
        }
    }
}
