package com.davidlang.vehicleexpensesautomated.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot device GPS for fill / trip / expense camera paths.
 * Never throws; returns null when permission missing, timed out, or fix unavailable.
 */
object CaptureLocation {
    private const val TAG = "CaptureLocation"
    private const val DEFAULT_TIMEOUT_MS = 2500L

    suspend fun captureLocationOrNull(
        context: Context,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Location? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            Log.i(TAG, "No location permission; returning null")
            return@withContext null
        }
        withTimeoutOrNull(timeoutMs) {
            tryFused(appContext) ?: tryLastKnown(appContext)
        }.also { loc ->
            if (loc != null) {
                Log.i(TAG, "Got fix lat=${loc.latitude} lon=${loc.longitude} provider=${loc.provider}")
            } else {
                Log.w(TAG, "No location fix within ${timeoutMs}ms")
            }
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun tryFused(context: Context): Location? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val current = suspendCancellableCoroutine<Location?> { cont ->
                cont.invokeOnCancellation { cts.cancel() }
                try {
                    client.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token,
                    ).addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }.addOnFailureListener { e ->
                        Log.w(TAG, "getCurrentLocation failed: ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "getCurrentLocation security: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    Log.w(TAG, "getCurrentLocation error: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
            if (current != null) return current

            suspendCancellableCoroutine { cont ->
                try {
                    client.lastLocation
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "lastLocation failed: ${e.message}")
                            if (cont.isActive) cont.resume(null)
                        }
                } catch (e: SecurityException) {
                    Log.w(TAG, "lastLocation security: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    Log.w(TAG, "lastLocation error: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fused path failed: ${e.message}")
            null
        }
    }

    private fun tryLastKnown(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            var best: Location? = null
            for (p in providers) {
                if (!lm.isProviderEnabled(p)) continue
                val loc = try {
                    lm.getLastKnownLocation(p)
                } catch (_: SecurityException) {
                    null
                } catch (_: Exception) {
                    null
                } ?: continue
                if (best == null || loc.time > best.time) best = loc
            }
            best
        } catch (e: Exception) {
            Log.w(TAG, "LocationManager fallback failed: ${e.message}")
            null
        }
    }
}
