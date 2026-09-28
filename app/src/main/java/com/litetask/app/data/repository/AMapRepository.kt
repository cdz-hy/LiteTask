package com.litetask.app.data.repository

import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.model.AMapRouteData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AMapRepository @Inject constructor(
    private val preferenceManager: PreferenceManager
) {
    /**
     * 简单地理编码实现
     */
    suspend fun geocodeLocation(address: String): AMapRouteData? = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return@withContext null
        
        try {
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            val urlString = "https://restapi.amap.com/v3/geocode/geo?address=$encodedAddress&output=JSON&key=$key"
            val response = makeGetRequest(urlString) ?: return@withContext null
            val json = JSONObject(response)
            
            if (json.optString("status") == "1") {
                val geocodes = json.optJSONArray("geocodes")
                if (geocodes != null && geocodes.length() > 0) {
                    val first = geocodes.getJSONObject(0)
                    val locationStr = first.optString("location") // "lng,lat"
                    val parts = locationStr.split(",")
                    if (parts.size == 2) {
                        return@withContext AMapRouteData(
                            startName = "我的位置",
                            endName = address,
                            endAddress = first.optString("formatted_address"),
                            endLng = parts[0].toDoubleOrNull() ?: 0.0,
                            endLat = parts[1].toDoubleOrNull() ?: 0.0,
                            adcode = first.optString("adcode")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * 搜索地点建议
     */
    suspend fun searchLocations(keyword: String): List<AMapRouteData> = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank() || keyword.isBlank()) return@withContext emptyList()
        
        try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val urlString = "https://restapi.amap.com/v3/assistant/inputtips?keywords=$encodedKeyword&output=JSON&key=$key"
            val response = makeGetRequest(urlString) ?: return@withContext emptyList()
            val json = JSONObject(response)
            
            if (json.optString("status") == "1") {
                val tips = json.optJSONArray("tips") ?: return@withContext emptyList()
                val result = mutableListOf<AMapRouteData>()
                
                for (i in 0 until tips.length()) {
                    val tip = tips.getJSONObject(i)
                    val locationStr = tip.optString("location")
                    if (locationStr.isNullOrBlank()) continue
                    
                    val parts = locationStr.split(",")
                    if (parts.size == 2) {
                        val district = (tip.opt("district") as? String) ?: ""
                        val address = (tip.opt("address") as? String) ?: ""
                        result.add(AMapRouteData(
                            startName = "我的位置",
                            endName = tip.optString("name"),
                            endAddress = district + address,
                            endLng = parts[0].toDoubleOrNull() ?: 0.0,
                            endLat = parts[1].toDoubleOrNull() ?: 0.0,
                            adcode = tip.opt("adcode") as? String
                        ))
                    }
                }
                return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    /**
     * 获取实况天气
     */
    suspend fun fetchWeather(adcode: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank() || adcode.isBlank()) return@withContext null
        
        try {
            val urlString = "https://restapi.amap.com/v3/weather/weatherInfo?city=$adcode&key=$key&extensions=base"
            val response = makeGetRequest(urlString) ?: return@withContext null
            val json = JSONObject(response)
            
            if (json.optString("status") == "1") {
                val lives = json.optJSONArray("lives")
                if (lives != null && lives.length() > 0) {
                    val live = lives.getJSONObject(0)
                    return@withContext Pair(
                        live.optString("weather"),
                        live.optString("temperature")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * 获取未来几天天气预报（extensions=all）
     */
    suspend fun fetchWeatherForecast(adcode: String): AMapWeatherReport? = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank() || adcode.isBlank()) return@withContext null
        try {
            val encodedAdcode = URLEncoder.encode(adcode.trim(), "UTF-8")
            val urlString = "https://restapi.amap.com/v3/weather/weatherInfo?city=$encodedAdcode&key=$key&extensions=all"
            val response = makeGetRequest(urlString) ?: return@withContext null
            parseWeatherForecastResponse(response)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过坐标轻量解析高德区域编码 adcode（避免搜索虚拟建筑失败）
     */
    suspend fun reverseGeocodeAdcode(lng: Double, lat: Double): String? = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return@withContext null
        try {
            val urlString = "https://restapi.amap.com/v3/geocode/regeo?location=$lng,$lat&key=$key&output=JSON&extensions=base"
            val response = makeGetRequest(urlString) ?: return@withContext null
            val json = JSONObject(response)
            if (json.optString("status") == "1") {
                val addr = json.optJSONObject("regeocode")?.optJSONObject("addressComponent")
                    ?: return@withContext null
                val adcode = (addr.opt("adcode") as? String)?.trim()
                return@withContext adcode?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * 周边搜索 (POI 搜索)
     * @param keyword 关键词
     * @param location 中心点 "lng,lat"
     * @param radius 半径 (米)，默认 5000
     */
    suspend fun searchNearby(keyword: String, location: String, radius: Int = 50000): List<AMapPoiResult> = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank() || keyword.isBlank() || location.isBlank()) return@withContext emptyList()

        try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val urlString = "https://restapi.amap.com/v3/place/around?key=$key&location=$location&keywords=$encodedKeyword&radius=$radius&output=JSON"
            val response = makeGetRequest(urlString) ?: return@withContext emptyList()
            val json = JSONObject(response)

            if (json.optString("status") == "1") {
                val pois = json.optJSONArray("pois") ?: return@withContext emptyList()
                val result = mutableListOf<AMapPoiResult>()

                for (i in 0 until pois.length()) {
                    val poi = pois.getJSONObject(i)
                    val locStr = poi.optString("location")
                    if (locStr.isNullOrBlank()) continue

                    val parts = locStr.split(",")
                    if (parts.size == 2) {
                        result.add(AMapPoiResult(
                            name = poi.optString("name"),
                            address = (poi.opt("address") as? String) ?: "",
                            lng = parts[0].toDoubleOrNull() ?: 0.0,
                            lat = parts[1].toDoubleOrNull() ?: 0.0,
                            adcode = poi.opt("adcode") as? String,
                            type = poi.opt("type") as? String,
                            distance = poi.optString("distance").toIntOrNull()
                        ))
                    }
                }
                // 按距离自动排序（高德 API 默认已按距离排序）
                return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    /**
     * 通过坐标解析城市名称（用于公交路线规划的必填 city 参数）或区域编码
     */
    suspend fun resolveCityForCoord(lng: Double, lat: Double): String? = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return@withContext null

        try {
            val urlString = "https://restapi.amap.com/v3/geocode/regeo?location=$lng,$lat&key=$key&output=JSON&extensions=base"
            val response = makeGetRequest(urlString) ?: return@withContext null
            val json = JSONObject(response)
            if (json.optString("status") == "1") {
                val addr = json.optJSONObject("regeocode")?.optJSONObject("addressComponent")
                    ?: return@withContext null
                val city = addr.opt("city") as? String
                val province = addr.opt("province") as? String
                val adcode = addr.opt("adcode") as? String
                return@withContext listOfNotNull(city, province, adcode)
                    .firstOrNull { it.isNotBlank() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * 逆地理编码详情（供 LocationTracker 等上层使用，同时避免直接解析 JSON 数组异常）
     */
    suspend fun reverseGeocodeFull(lng: Double, lat: Double): String? = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return@withContext null
        val urlString = "https://restapi.amap.com/v3/geocode/regeo?location=$lng,$lat&key=$key&output=JSON&extensions=all"
        makeGetRequest(urlString)
    }

    /**
     * 路线规划
     * @param mode 模式: driving, walking, bicycling, transit
     * @return Pair(耗时秒数, 距离米数) 或 null
     */
    suspend fun calculateRoute(
        originLng: Double,
        originLat: Double,
        destLng: Double,
        destLat: Double,
        mode: String = "driving"
    ): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return@withContext null

        try {
            val normalizedMode = mode.lowercase()
            val city = if (normalizedMode == "transit") {
                resolveCityForCoord(originLng, originLat) ?: "北京"
            } else {
                null
            }
            val urlString = buildRouteUrl(originLng, originLat, destLng, destLat, normalizedMode, key, city)
            val response = makeGetRequest(urlString) ?: return@withContext null
            return@withContext parseRouteResponse(response, normalizedMode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    companion object {
        internal fun buildRouteUrl(
            originLng: Double,
            originLat: Double,
            destLng: Double,
            destLat: Double,
            mode: String,
            key: String,
            city: String? = null
        ): String {
            val origin = "$originLng,$originLat"
            val dest = "$destLng,$destLat"
            return when (mode.lowercase()) {
                "walking" ->
                    "https://restapi.amap.com/v3/direction/walking?origin=$origin&destination=$dest&key=$key"
                "bicycling" ->
                    "https://restapi.amap.com/v4/direction/bicycling?origin=$origin&destination=$dest&key=$key"
                "transit" -> {
                    val encodedCity = URLEncoder.encode(city?.takeIf { it.isNotBlank() } ?: "北京", "UTF-8")
                    "https://restapi.amap.com/v3/direction/transit/integrated?origin=$origin&destination=$dest&city=$encodedCity&key=$key"
                }
                else ->
                    "https://restapi.amap.com/v3/direction/driving?origin=$origin&destination=$dest&key=$key"
            }
        }

        internal fun parseWeatherForecastResponse(response: String): AMapWeatherReport? {
            return try {
                val json = JSONObject(response)
                if (json.optString("status") != "1") return null
                val forecasts = json.optJSONArray("forecasts") ?: return null
                if (forecasts.length() == 0) return null
                val first = forecasts.getJSONObject(0)
                val casts = first.optJSONArray("casts") ?: return null
                val days = mutableListOf<AMapWeatherForecastDay>()
                for (i in 0 until casts.length()) {
                    val cast = casts.getJSONObject(i)
                    days.add(
                        AMapWeatherForecastDay(
                            date = cast.optString("date", ""),
                            week = cast.optString("week", ""),
                            dayWeather = cast.optString("dayweather", ""),
                            nightWeather = cast.optString("nightweather", ""),
                            dayTemp = cast.optString("daytemp", ""),
                            nightTemp = cast.optString("nighttemp", ""),
                            dayWind = cast.optString("daywind", ""),
                            nightWind = cast.optString("nightwind", "")
                        )
                    )
                }
                if (days.isEmpty()) return null
                AMapWeatherReport(
                    city = first.optString("city", ""),
                    adcode = first.optString("adcode", ""),
                    reportTime = first.optString("reporttime", ""),
                    forecasts = days
                )
            } catch (_: Exception) {
                null
            }
        }

        internal fun parseRouteResponse(response: String, mode: String): Pair<Int, Int>? {
            return try {
                val json = JSONObject(response)
                when (mode.lowercase()) {
                    "bicycling" -> {
                        val data = json.optJSONObject("data") ?: return null
                        if (json.optInt("errcode", -1) != 0 && json.optString("status") != "1") return null
                        val paths = data.optJSONArray("paths") ?: return null
                        if (paths.length() == 0) return null
                        val path = paths.getJSONObject(0)
                        val duration = path.optInt("duration", path.optString("duration", "0").toIntOrNull() ?: 0)
                        val distance = path.optInt("distance", path.optString("distance", "0").toIntOrNull() ?: 0)
                        Pair(duration, distance)
                    }
                    "transit" -> {
                        if (json.optString("status") != "1") return null
                        val transits = json.optJSONObject("route")?.optJSONArray("transits") ?: return null
                        if (transits.length() == 0) return null
                        val transit = transits.getJSONObject(0)
                        val duration = transit.optString("duration", "0").toIntOrNull() ?: 0
                        val distance = transit.optString("distance", "0").toIntOrNull() ?: 0
                        Pair(duration, distance)
                    }
                    else -> {
                        if (json.optString("status") != "1") return null
                        val paths = json.optJSONObject("route")?.optJSONArray("paths") ?: return null
                        if (paths.length() == 0) return null
                        val path = paths.getJSONObject(0)
                        val duration = path.optString("duration", "0").toIntOrNull() ?: 0
                        val distance = path.optString("distance", "0").toIntOrNull() ?: 0
                        Pair(duration, distance)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun makeGetRequest(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class AMapPoiResult(
    val name: String,
    val address: String?,
    val lng: Double,
    val lat: Double,
    val adcode: String?,
    val type: String?,
    val distance: Int?
)

data class AMapWeatherForecastDay(
    val date: String,
    val week: String,
    val dayWeather: String,
    val nightWeather: String,
    val dayTemp: String,
    val nightTemp: String,
    val dayWind: String,
    val nightWind: String
)

data class AMapWeatherReport(
    val city: String,
    val adcode: String,
    val reportTime: String,
    val forecasts: List<AMapWeatherForecastDay>
)
