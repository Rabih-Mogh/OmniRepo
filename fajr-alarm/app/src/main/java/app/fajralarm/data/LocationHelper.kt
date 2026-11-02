package app.fajralarm.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * One-shot coarse location using the platform LocationManager (no Google Play
 * services dependency). Caller must hold ACCESS_COARSE_LOCATION.
 */
object LocationHelper {

    @SuppressLint("MissingPermission")
    fun requestOnce(context: Context, callback: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        // A recent last-known fix is good enough: Fajr barely changes over a few km.
        val lastKnown = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 6 * 3600_000L) {
            callback(lastKnown)
            return
        }

        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        if (provider == null) {
            callback(lastKnown)
            return
        }

        var delivered = false
        val handler = Handler(Looper.getMainLooper())
        fun deliver(loc: Location?) {
            if (delivered) return
            delivered = true
            callback(loc ?: lastKnown)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { deliver(it) }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    deliver(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            handler.postDelayed({ lm.removeUpdates(listener) }, 30_000)
        }
        handler.postDelayed({ deliver(null) }, 30_000)
    }
}
