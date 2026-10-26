package com.litetask.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FRESH_CACHE_MAX_AGE_MS = 3 * 60 * 1000L
        private const val FALLBACK_MAX_AGE_MS = 2 * 60 * 60 * 1000L
        private const val REQUEST_TIMEOUT_MS = 5000L
    }

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

    /**
     * 快速读取最近 2 小时内的系统定位缓存（已转 GCJ-02），不主动触发硬件定位。
     */
    @SuppressLint("MissingPermission")
    fun getCachedLocationOnly(maxAgeMs: Long = FALLBACK_MAX_AGE_MS): Location? {
        if (!hasLocationPermission()) return null
        val best = findBestCachedLocation(maxAgeMs)?.first ?: return null
        return toGcj02Location(best)
    }

    @SuppressLint("MissingPermission")
    private fun findBestCachedLocation(maxAgeMs: Long): Pair<Location, Long>? {
        val providers = locationManager.getProviders(true)
        if (providers.isEmpty()) return null
        val now = System.currentTimeMillis()
        var best: Location? = null
        for (provider in providers) {
            val last = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            } ?: continue
            if (now - last.time > maxAgeMs) continue
            if (best == null ||
                last.time > best.time + 60_000L ||
                last.accuracy < best.accuracy
            ) {
                best = last
            }
        }
        return best?.let { it to (now - it.time) }
    }

    /**
     * 获取当前位置（返回 GCJ-02 坐标系供高德与足迹使用）。
     * @param maxAgeMs 允许直接复用系统缓存定位的最大年龄，默认 3 分钟。
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(
        maxAgeMs: Long = FRESH_CACHE_MAX_AGE_MS
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val providers = locationManager.getProviders(true)
        if (providers.isEmpty()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val cachedWithAge = findBestCachedLocation(FALLBACK_MAX_AGE_MS)
        // maxAgeMs 内的缓存直接复用（减少频繁唤醒 GPS/网络定位）
        if (cachedWithAge != null && cachedWithAge.second < maxAgeMs) {
            continuation.resume(toGcj02Location(cachedWithAge.first))
            return@suspendCancellableCoroutine
        }

        val fallbackLocation = cachedWithAge?.first?.let(::toGcj02Location)
        var isResolved = false
        val mainHandler = Handler(Looper.getMainLooper())
        val cancellationSignal = CancellationSignal()

        val timeoutRunnable = Runnable {
            if (continuation.isActive && !isResolved) {
                isResolved = true
                cancellationSignal.cancel()
                continuation.resume(fallbackLocation)
            }
        }

        val provider = pickPreferredProvider(providers)
        if (provider == null) {
            continuation.resume(fallbackLocation)
            return@suspendCancellableCoroutine
        }

        try {
            mainHandler.postDelayed(timeoutRunnable, REQUEST_TIMEOUT_MS)
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context)
            ) { location ->
                mainHandler.removeCallbacks(timeoutRunnable)
                if (continuation.isActive && !isResolved) {
                    isResolved = true
                    continuation.resume(location?.let(::toGcj02Location) ?: fallbackLocation)
                }
            }
        } catch (_: Exception) {
            mainHandler.removeCallbacks(timeoutRunnable)
            if (continuation.isActive && !isResolved) {
                isResolved = true
                continuation.resume(fallbackLocation)
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.removeCallbacks(timeoutRunnable)
            cancellationSignal.cancel()
        }
    }

    private fun pickPreferredProvider(enabledProviders: List<String>): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            enabledProviders.contains(LocationManager.FUSED_PROVIDER)
        ) {
            return LocationManager.FUSED_PROVIDER
        }
        if (enabledProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER
        }
        if (enabledProviders.contains(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER
        }
        return enabledProviders.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
            ?: enabledProviders.firstOrNull()
    }

    private fun toGcj02Location(raw: Location): Location {
        val (mgLng, mgLat) = GeoCoordinateUtils.wgs84ToGcj02(raw.longitude, raw.latitude)
        return Location(raw).apply {
            longitude = mgLng
            latitude = mgLat
        }
    }
}
