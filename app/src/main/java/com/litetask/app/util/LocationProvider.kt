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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

        // 如果有近期的缓存（30分钟内），直接使用
        if (bestLocation != null && System.currentTimeMillis() - bestLocation.time < 30 * 60 * 1000) {
            continuation.resume(bestLocation)
            return@suspendCancellableCoroutine
        }

        // 如果缓存较旧但存在，先保存作为备用
        val fallbackLocation = bestLocation

        // 尝试请求一次新的位置更新（带超时）
        var isResolved = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (continuation.isActive && !isResolved) {
                    isResolved = true
                    continuation.resume(location)
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // 优先使用网络定位（更快）
            val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                // 没有可用的定位提供者，使用备用位置
                if (continuation.isActive && !isResolved) {
                    isResolved = true
                    continuation.resume(fallbackLocation)
                }
                return@suspendCancellableCoroutine
            }
            
            locationManager.requestSingleUpdate(provider, listener, null)
            
            // 设置超时：3秒后如果还没有获取到新位置，使用备用位置
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.delay(3000)
                if (continuation.isActive && !isResolved) {
                    isResolved = true
                    locationManager.removeUpdates(listener)
                    // 使用备用位置（即使较旧也比没有好）
                    continuation.resume(fallbackLocation)
                }
            }
        } catch (e: Exception) {
            if (continuation.isActive && !isResolved) {
                isResolved = true
                // 发生异常时，使用备用位置
                continuation.resume(fallbackLocation)
            }
        }

        continuation.invokeOnCancellation {
            locationManager.removeUpdates(listener)
        }
    }
}
