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
                        result.add(AMapRouteData(
                            startName = "我的位置",
                            endName = tip.optString("name"),
                            endAddress = tip.optString("district") + tip.optString("address"),
                            endLng = parts[0].toDoubleOrNull() ?: 0.0,
                            endLat = parts[1].toDoubleOrNull() ?: 0.0,
                            adcode = tip.optString("adcode")
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
