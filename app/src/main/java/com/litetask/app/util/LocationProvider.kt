package com.litetask.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val providers = locationManager.getProviders(true)
        if (providers.isEmpty()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        // 尝试获取最后一次已知位置
        var bestLocation: Location? = null
        for (provider in providers) {
            val lastLocation = locationManager.getLastKnownLocation(provider)
            if (lastLocation != null) {
                if (bestLocation == null || lastLocation.accuracy < bestLocation.accuracy) {
                    bestLocation = lastLocation
                }
            }
        }

        if (bestLocation != null && System.currentTimeMillis() - bestLocation.time < 5 * 60 * 1000) {
            continuation.resume(bestLocation)
            return@suspendCancellableCoroutine
        }

        // 如果没有近期的缓存，请求一次更新
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (continuation.isActive) continuation.resume(location)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                LocationManager.GPS_PROVIDER
            }
            locationManager.requestSingleUpdate(provider, listener, null)
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }

        continuation.invokeOnCancellation {
            locationManager.removeUpdates(listener)
        }
    }
}
