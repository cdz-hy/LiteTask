package com.litetask.app.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理坐标转换与距离计算工具（纯 Kotlin，可直接在 JVM 单元测试）。
 *
 * 系统 LocationManager 返回 WGS-84 坐标，而高德地图 Web API 使用 GCJ-02（火星坐标系）。
 * 若不转换，在中国境内通常会产生 300~700 米偏移。
 */
object GeoCoordinateUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val AXIS = 6378245.0
    private const val EE = 0.00669342162296594323

    /**
     * 判断坐标是否位于中国境内大致边界，境外坐标保持 WGS-84 原值。
     */
    fun isInChinaBounds(lng: Double, lat: Double): Boolean {
        return lng in 72.004..137.8347 && lat in 0.8293..55.8271
    }

    /**
     * WGS-84 转 GCJ-02（返回 Pair(lng, lat)）。
     */
    fun wgs84ToGcj02(lng: Double, lat: Double): Pair<Double, Double> {
        if (!isInChinaBounds(lng, lat)) {
            return lng to lat
        }
        val dLat = transformLat(lng - 105.0, lat - 35.0)
        val dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val mgLat = lat + (dLat * 180.0) / ((AXIS * (1 - EE)) / (magic * sqrtMagic) * PI)
        val mgLng = lng + (dLng * 180.0) / (AXIS / sqrtMagic * cos(radLat) * PI)
        return mgLng to mgLat
    }

    /**
     * Haversine 球面距离（单位：米）。
     */
    fun distanceMeters(lng1: Double, lat1: Double, lng2: Double, lat2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(radLat1) * cos(radLat2) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
