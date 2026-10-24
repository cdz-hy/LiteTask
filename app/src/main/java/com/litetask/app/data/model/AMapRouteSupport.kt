package com.litetask.app.data.model

import com.litetask.app.data.repository.AMapWeatherForecastDay
import com.litetask.app.data.repository.AMapWeatherReport
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 高德路线与出行辅助纯逻辑。
 * 不依赖 Android Framework，便于全量 JVM 单元测试覆盖。
 */
object AMapRouteSupport {

    enum class TravelMode(
        val code: String,
        val label: String,
        val amapCode: Int,
        val baiduMode: String,
        val qqType: String,
        val webMode: String
    ) {
        DRIVING("driving", "驾车", 0, "driving", "drive", "car"),
        TRANSIT("transit", "公交地铁", 1, "transit", "bus", "bus"),
        WALKING("walking", "步行", 2, "walking", "walk", "walk"),
        BICYCLING("bicycling", "骑行", 3, "riding", "bike", "ride");

        companion object {
            fun fromCode(code: String?): TravelMode {
                val normalized = code?.trim()?.lowercase(Locale.ROOT)
                return entries.firstOrNull { it.code == normalized } ?: DRIVING
            }
        }
    }

    enum class DrivingStrategy(val code: Int, val label: String) {
        SPEED(0, "速度优先"),
        AVOID_TOLL(1, "少收费"),
        SHORT_DISTANCE(2, "距离优先");

        companion object {
            fun fromCode(code: Int): DrivingStrategy =
                entries.firstOrNull { it.code == code } ?: SPEED
        }
    }

    data class MatchedWeatherForecast(
        val dayLabel: String,
        val weather: String,
        val tempRange: String,
        val day: AMapWeatherForecastDay
    )

    data class MapNavigationTarget(
        val packageName: String?,
        val uriString: String,
        val appDisplayName: String
    )

    fun AMapRouteData.resolvedTravelMode(): TravelMode = TravelMode.fromCode(travelMode)

    fun AMapRouteData.resolvedDrivingStrategy(): DrivingStrategy = DrivingStrategy.fromCode(strategy)

    fun AMapRouteData.hasExplicitOrigin(): Boolean =
        startLat != null && startLng != null && startLat != 0.0 && startLng != 0.0

    fun formatDurationShort(durationSec: Int?): String? {
        if (durationSec == null || durationSec <= 0) return null
        val totalMinutes = ((durationSec + 59) / 60).coerceAtLeast(1)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0 -> "约${totalMinutes}分钟"
            minutes == 0 -> "约${hours}小时"
            else -> "约${hours}小时${minutes}分钟"
        }
    }

    fun formatDistanceShort(distanceMeters: Int?): String? {
        if (distanceMeters == null || distanceMeters <= 0) return null
        return if (distanceMeters < 1000) {
            "${distanceMeters}m"
        } else {
            String.format(Locale.US, "%.1fkm", distanceMeters / 1000.0)
        }
    }

    fun formatEtaSummary(durationSec: Int?, distanceMeters: Int?): String? {
        val durationPart = formatDurationShort(durationSec)
        val distancePart = formatDistanceShort(distanceMeters)
        return when {
            durationPart != null && distancePart != null -> "$durationPart · $distancePart"
            durationPart != null -> durationPart
            distancePart != null -> distancePart
            else -> null
        }
    }

    /**
     * 计算建议出发提醒时间戳（开始时间 - 预估路程 - 缓冲时间）。
     */
    fun calculateSuggestedDepartureMillis(
        taskStartTime: Long,
        estimatedDurationSec: Int?,
        bufferMinutes: Int = 10
    ): Long? {
        if (taskStartTime <= 0L || estimatedDurationSec == null || estimatedDurationSec <= 0) {
            return null
        }
        val leadMs = (estimatedDurationSec.toLong() + bufferMinutes.coerceAtLeast(0) * 60L) * 1000L
        val triggerAt = taskStartTime - leadMs
        return triggerAt.takeIf { it > 0L }
    }

    /**
     * 将高德 3-4 天预报精确匹配到任务目标日期（今天/明天/后天/具体日期）。
     */
    fun matchForecastForTaskDate(
        report: AMapWeatherReport,
        targetTimeMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): MatchedWeatherForecast? {
        if (targetTimeMillis <= 0L || report.forecasts.isEmpty()) return null
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        val targetDate = formatter.format(Date(targetTimeMillis))
        val matched = report.forecasts.firstOrNull { it.date.trim() == targetDate } ?: return null

        val todayDay = dayOrdinal(nowMillis, timeZone)
        val targetDay = dayOrdinal(targetTimeMillis, timeZone)
        val dayLabel = when (targetDay - todayDay) {
            0 -> "今天"
            1 -> "明天"
            2 -> "后天"
            else -> targetDate.substringAfter("-")
        }
        val targetHour = Calendar.getInstance(timeZone).apply { timeInMillis = targetTimeMillis }
            .get(Calendar.HOUR_OF_DAY)
        val isNight = targetHour >= 18 || targetHour < 6
        val weatherText = if (isNight && matched.nightWeather.isNotBlank()) {
            matched.nightWeather
        } else {
            matched.dayWeather.ifBlank { matched.nightWeather }
        }
        val tempRange = if (matched.nightTemp.isNotBlank() && matched.dayTemp.isNotBlank()) {
            if (matched.nightTemp == matched.dayTemp) {
                "${matched.dayTemp}°C"
            } else {
                "${matched.nightTemp}~${matched.dayTemp}°C"
            }
        } else {
            matched.dayTemp.ifBlank { matched.nightTemp }.let { if (it.isNotBlank()) "${it}°C" else "" }
        }
        if (weatherText.isBlank() && tempRange.isBlank()) return null

        return MatchedWeatherForecast(
            dayLabel = dayLabel,
            weather = weatherText,
            tempRange = tempRange,
            day = matched
        )
    }

    /**
     * 构建多地图应用及网页降级跳转目标列表（高德 -> 百度 -> 腾讯 -> geo URI -> 高德网页版）。
     */
    fun buildMapNavigationTargets(data: AMapRouteData): List<MapNavigationTarget> {
        val mode = data.resolvedTravelMode()
        val encodedDest = urlEncode(data.endName.ifBlank { "目的地" })
        val encodedStart = urlEncode(data.startName.ifBlank { "我的位置" })

        val amapUri = buildString {
            append("androidamap://route/plan/?sourceApplication=LiteTask")
            if (data.hasExplicitOrigin()) {
                append("&slat=").append(data.startLat)
                append("&slon=").append(data.startLng)
                append("&sname=").append(encodedStart)
            }
            append("&dlat=").append(data.endLat)
            append("&dlon=").append(data.endLng)
            append("&dname=").append(encodedDest)
            append("&dev=0&t=").append(mode.amapCode)
        }

        val baiduOriginPart = if (data.hasExplicitOrigin()) {
            "origin=latlng:${data.startLat},${data.startLng}|name:$encodedStart&"
        } else {
            ""
        }
        val baiduUri = "baidumap://map/direction?${baiduOriginPart}destination=latlng:${data.endLat},${data.endLng}|name:$encodedDest&coord_type=gcj02&mode=${mode.baiduMode}&src=andr.litetask"

        val qqFromPart = if (data.hasExplicitOrigin()) {
            "from=$encodedStart&fromcoord=${data.startLat},${data.startLng}&"
        } else {
            ""
        }
        val qqUri = "qqmap://map/routeplan?type=${mode.qqType}&${qqFromPart}to=$encodedDest&tocoord=${data.endLat},${data.endLng}&referer=LiteTask"

        val geoUri = "geo:${data.endLat},${data.endLng}?q=${data.endLat},${data.endLng}($encodedDest)"

        val webOriginPart = if (data.hasExplicitOrigin()) {
            "from=${data.startLng},${data.startLat},$encodedStart&"
        } else {
            ""
        }
        val webUri = "https://uri.amap.com/navigation?${webOriginPart}to=${data.endLng},${data.endLat},$encodedDest&mode=${mode.webMode}&src=LiteTask&coordinate=gaode"

        return listOf(
            MapNavigationTarget("com.autonavi.minimap", amapUri, "高德地图"),
            MapNavigationTarget("com.baidu.BaiduMap", baiduUri, "百度地图"),
            MapNavigationTarget("com.tencent.map", qqUri, "腾讯地图"),
            MapNavigationTarget(null, geoUri, "系统地图"),
            MapNavigationTarget(null, webUri, "网页导航")
        )
    }

    private fun dayOrdinal(timeMillis: Long, timeZone: TimeZone): Int {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMillis
        }
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
