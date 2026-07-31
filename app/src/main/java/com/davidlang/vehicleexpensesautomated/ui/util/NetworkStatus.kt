package com.davidlang.vehicleexpensesautomated.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Connectivity helpers for greying online-only UI (POI lookup, etc.).
 * INTERNET is a normal install-time permission — not a runtime grant/deny dialog.
 * Capture / local save / reports must never depend on this.
 */
object NetworkStatus {

    /**
     * True when the active network reports INTERNET capability.
     * Prefer VALIDATED when available so captive portals don't look "online".
     */
    fun hasUsableNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (!hasInternet) return false
                // VALIDATED means default route actually works (API 23+ on many devices)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Not all emulators set VALIDATED; accept INTERNET alone if not validated flag missing
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        return true
                    }
                    // Fallback: transport present + INTERNET (airplane-off, no validation yet)
                    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
                true
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }
}
