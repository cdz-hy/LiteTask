package com.litetask.app.data.repository

import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.local.UserLocationDao
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.UserLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 地点追踪器
 * 负责在 Agent 任务分析过程中记录和保存地点数据
 */
@Singleton
class LocationTracker @Inject constructor(
    private val userLocationDao: UserLocationDao,
    private val aMapRepository: AMapRepository,
    private val preferenceManager: PreferenceManager,
    private val aiProviderFactory: com.litetask.app.data.ai.AIProviderFactory
) {
    
    /**
     * 逆地理编码：将经纬度转换为真实地名
     * 使用 AI 辅助选择最合适的地点名称
     */
    suspend fun reverseGeocode(lng: Double, lat: Double): String = withContext(Dispatchers.IO) {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return@withContext "当前位置"
        
        try {
            val urlString = "https://restapi.amap.com/v3/geocode/regeo?location=$lng,$lat&key=$key&output=JSON&extensions=all"
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (json.optString("status") == "1") {
                    val regeocode = json.optJSONObject("regeocode")
                    val addressComponent = regeocode?.optJSONObject("addressComponent")
                    
                    // 收集候选地点信息
                    val candidates = mutableListOf<String>()
                    
                    // 1. POI 信息（最优先）
                    val pois = regeocode?.optJSONArray("pois")
                    if (pois != null && pois.length() > 0) {
                        for (i in 0 until minOf(3, pois.length())) {
                            val poi = pois.getJSONObject(i)
                            val poiName = poi.optString("name", "")
                            val poiType = poi.optString("type", "")
                            if (poiName.isNotBlank()) {
                                candidates.add("$poiName (类型: $poiType)")
                            }
                        }
                    }
                    
                    // 2. 社区/小区信息
                    val neighborhood = addressComponent?.optString("neighborhood", "") ?: ""
                    if (neighborhood.isNotBlank()) {
                        candidates.add("$neighborhood (社区)")
                    }
                    
                    // 3. 街道信息
                    val township = addressComponent?.optString("township", "") ?: ""
                    if (township.isNotBlank()) {
                        candidates.add("$township (街道)")
                    }
                    
                    // 4. 区域信息
                    val district = addressComponent?.optString("district", "") ?: ""
                    if (district.isNotBlank()) {
                        candidates.add("$district (区域)")
                    }
                    
                    // 如果有候选地点，使用 AI 选择最合适的
                    if (candidates.isNotEmpty()) {
                        return@withContext selectBestLocationName(candidates)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext "当前位置"
    }
    
    /**
     * 使用 AI 选择最合适的地点名称
     */
    private suspend fun selectBestLocationName(candidates: List<String>): String {
        if (candidates.isEmpty()) return "当前位置"
        if (candidates.size == 1) {
            // 只有一个候选，直接提取名称
            return extractLocationName(candidates[0])
        }
        
        try {
            val apiKey = preferenceManager.getApiKey()
            if (apiKey.isNullOrBlank()) {
                // 没有 API Key，使用默认规则：优先 POI
                return extractLocationName(candidates[0])
            }
            
            val providerId = preferenceManager.getAiProvider()
            val modelId = preferenceManager.getAiModel()
            val provider = aiProviderFactory.getProvider(providerId)
            
            val prompt = """
                从以下候选地点中选择最适合作为"出发地"的地点名称。
                
                候选地点：
                ${candidates.joinToString("\n") { "- $it" }}
                
                选择规则：
                1. 优先选择较大范围的标志性地点（如：某某大学、某某园区、某某商圈、某某小区），而不是极其具体的门牌号、楼栋或房间号。
                2. 这样做的目的是为了保护用户隐私，并让"常在地"的统计更具代表性。
                3. 如果有多个 POI，选择最能代表该区域的名称。
                4. 只返回地点名称本身，不要包含类型说明。
                
                直接返回最合适的地点名称，不要有任何解释或额外文字。
            """.trimIndent()
            
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            
            val result = provider.chatWithTools(apiKey, modelId, messages, JSONArray())
            if (result.isSuccess) {
                val responseJson = result.getOrNull()
                val choice = responseJson?.optJSONArray("choices")?.optJSONObject(0)
                val message = choice?.optJSONObject("message")
                val content = message?.optString("content", "")?.trim() ?: ""
                
                if (content.isNotBlank() && content != "当前位置") {
                    // 清理 AI 返回的内容，移除可能的引号、括号等
                    val cleanedName = content
                        .replace("\"", "")
                        .replace("'", "")
                        .replace(Regex("\\(.*?\\)"), "") // 移除括号及其内容
                        .trim()
                    
                    if (cleanedName.isNotBlank()) {
                        return cleanedName
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // AI 调用失败，使用默认规则
        return extractLocationName(candidates[0])
    }
    
    /**
     * 从候选字符串中提取地点名称（移除类型说明）
     */
    private fun extractLocationName(candidate: String): String {
        // 移除括号及其内容
        val name = candidate.replace(Regex("\\s*\\(.*?\\)"), "").trim()
        return if (name.isNotBlank()) name else "当前位置"
    }
    
    /**
     * 保存出发地数据
     */
    suspend fun saveOriginLocation(locationName: String, lng: Double, lat: Double) {
        if (locationName.isBlank() || locationName == "我的位置" || locationName == "当前位置") {
            return
        }
        
        try {
            val existing = userLocationDao.getLocationByName(locationName)
            if (existing != null) {
                // 更新出发次数
                userLocationDao.insertOrUpdate(
                    existing.copy(
                        originCount = existing.originCount + 1,
                        lastVisitedAt = System.currentTimeMillis()
                    )
                )
            } else {
                // 新增
                userLocationDao.insertOrUpdate(
                    UserLocationEntity(
                        name = locationName,
                        latitude = lat,
                        longitude = lng,
                        originCount = 1,
                        destinationCount = 0,
                        lastVisitedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 保存目的地数据（只保存最终确定的目的地）
     */
    suspend fun saveDestinationFromTasks(tasks: List<Task>) {
        tasks.forEach { task ->
            val destination = task.parsedDestination
            if (!destination.isNullOrBlank() && 
                destination != "我的位置" && 
                destination != "当前位置") {
                saveDestination(destination)
            }
        }
    }
    
    /**
     * 保存单个目的地
     */
    private suspend fun saveDestination(destination: String) {
        try {
            // 尝试通过地理编码获取坐标
            val routeData = aMapRepository.geocodeLocation(destination)
            if (routeData != null) {
                val existing = userLocationDao.getLocationByName(destination)
                if (existing != null) {
                    // 更新目的地次数
                    userLocationDao.insertOrUpdate(
                        existing.copy(
                            destinationCount = existing.destinationCount + 1,
                            lastVisitedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    // 新增
                    userLocationDao.insertOrUpdate(
                        UserLocationEntity(
                            name = destination,
                            latitude = routeData.endLat,
                            longitude = routeData.endLng,
                            originCount = 0,
                            destinationCount = 1,
                            lastVisitedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
