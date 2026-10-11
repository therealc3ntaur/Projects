package com.smartsiddur.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Reads the device's approximate location (no Google Play services needed). */
object DeviceLocation {

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Stores the current location in [settings]; returns false if none could be found. */
    suspend fun refresh(context: Context, settings: Settings): Boolean {
        val location = current(context) ?: return false
        settings.latitude = location.latitude.toFloat()
        settings.longitude = location.longitude.toFloat()
        return true
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { return it }
        // No cached fix: ask for a fresh one (Android 11+), giving up after a while
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in providers } ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                manager.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
            }
        }
    }
}
