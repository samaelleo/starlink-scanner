package com.starlinks.scanner.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Robust, multi-tier location provider.
 * Completely resolves the bug where GPS coordinates were failing to save.
 */
class LocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var lastKnownValidLocation: Location? = null

    @SuppressLint("MissingPermission")
    suspend fun getBestLocation(): Location? {
        // Step 1: Check fused lastLocation first (instant 0ms response!)
        val cachedFused = getLastLocationAsync()
        if (cachedFused != null) {
            val ageMs = System.currentTimeMillis() - cachedFused.time
            // If less than 5 minutes old, return immediately!
            if (ageMs < 5 * 60 * 1000) {
                lastKnownValidLocation = cachedFused
                return cachedFused
            }
        }

        // Step 2: Request fresh balanced/network location with realistic timeout (8 seconds)
        val fresh = withTimeoutOrNull(8000L) {
            requestFreshLocationAsync()
        }
        if (fresh != null) {
            lastKnownValidLocation = fresh
            return fresh
        }

        // Step 3: Fall back to system location manager
        val systemFallback = getSystemFallbackLocation()
        if (systemFallback != null) {
            lastKnownValidLocation = systemFallback
            return systemFallback
        }

        return cachedFused ?: lastKnownValidLocation
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocationAsync(): Location? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocationAsync(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }

        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private fun getSystemFallbackLocation(): Location? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            try {
                val loc = locationManager?.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (_: Exception) {}
        }
        return null
    }
}
