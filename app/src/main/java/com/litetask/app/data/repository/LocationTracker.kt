package com.litetask.app.data.repository

import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.local.UserLocationDao
import com.litetask.app.data.model.AMapRouteData
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.UserLocationEntity
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.litetask.app.util.GeoCoordinateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 地点追踪器
 * 负责将逆地理编码与用户确认后的任务地点合并统计至本地常去地点表。
 */
@Singleton
class LocationTracker @Inject constructor(
    private val userLocationDao: UserLocationDao,
    private val aMapRepository: AMapRepository,
    private val preferenceManager: PreferenceManager
) {
    data class ReverseGeoResult(
        val name: String,
        val address: String = ""
    )

    private data class PendingOrigin(
        val name: String,
        val lng: Double,
        val lat: Double,
        val address: String = "",
        val stagedAt: Long = System.currentTimeMillis()
    )

    internal enum class CandidateKind {
        AOI,
        NEIGHBORHOOD,
        POI,
        TOWNSHIP,
        DISTRICT
    }

    internal data class LocationCandidate(
        val name: String,
        val kind: CandidateKind,
        val poiType: String = "",
        val distanceMeters: Double? = null
    )

    @Volatile
    private var pendingOrigin: PendingOrigin? = null

    fun stagePendingOrigin(name: String, lng: Double, lat: Double, address: String = "") {
        if (isGenericLocationName(name)) return
        pendingOrigin = PendingOrigin(name.trim(), lng, lat, address.trim())
    }

    fun clearPendingOrigin() {
        pendingOrigin = null
    }

    suspend fun commitPendingOrigin() {
        val pending = pendingOrigin ?: return
        pendingOrigin = null
        // 超过 20 分钟未确认则视为过期
        if (System.currentTimeMillis() - pending.stagedAt > 20 * 60 * 1000L) return
        saveOriginLocation(pending.name, pending.lng, pending.lat, pending.address)
    }

    /**
     * 逆地理编码：将经纬度转换为脱敏后的本地代表性地名与完整地址（不额外消耗大模型 Token）。
     */
    suspend fun reverseGeocodeDetailed(lng: Double, lat: Double): ReverseGeoResult = withContext(Dispatchers.IO) {
        val response = try {
            aMapRepository.reverseGeocodeFull(lng, lat)
        } catch (_: Exception) {
            null
        } ?: return@withContext ReverseGeoResult("当前位置", "")

        return@withContext extractBestResultFromRegeoJson(response)
    }

    suspend fun reverseGeocode(lng: Double, lat: Double): String = reverseGeocodeDetailed(lng, lat).name

    /**
     * 保存出发地数据（尊重足迹开关，按 300 米优先聚合并入并刷新有效坐标与地址）
     */
    suspend fun saveOriginLocation(
        locationName: String,
        lng: Double,
        lat: Double,
        address: String = ""
    ) {
        if (!preferenceManager.isLocationFootprintEnabled()) return
        if (isGenericLocationName(locationName)) return
        val cleanName = locationName.trim()
        val cleanAddress = address.trim()

        try {
            val all = userLocationDao.getAllLocationsSync()
            val existing = findMatchingLocation(cleanName, lng, lat, all)
            if (existing != null) {
                val hasNewCoords = lng != 0.0 || lat != 0.0
                userLocationDao.insertOrUpdate(
                    existing.copy(
                        latitude = if (hasNewCoords) lat else existing.latitude,
                        longitude = if (hasNewCoords) lng else existing.longitude,
                        address = cleanAddress.ifBlank { existing.address },
                        originCount = existing.originCount + 1,
                        lastVisitedAt = System.currentTimeMillis()
                    )
                )
            } else {
                userLocationDao.insertOrUpdate(
                    UserLocationEntity(
                        name = cleanName,
                        latitude = lat,
                        longitude = lng,
                        originCount = 1,
                        destinationCount = 0,
                        address = cleanAddress,
                        lastVisitedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 保存确认后任务的解析目的地
     */
    suspend fun saveDestinationFromTasks(tasks: List<Task>) {
        if (!preferenceManager.isLocationFootprintEnabled()) return
        tasks.forEach { task ->
            val destination = task.parsedDestination
            if (!destination.isNullOrBlank() && !isGenericLocationName(destination)) {
                saveDestination(destination.trim())
            }
        }
    }

    /**
     * 直接使用已解析的高德组件数据保存目的地（避免重复发地理编码网络请求）
     */
    suspend fun saveDestinationFromRouteData(data: AMapRouteData) {
        if (!preferenceManager.isLocationFootprintEnabled()) return
        val name = data.endName.trim()
        if (isGenericLocationName(name)) return

        if (data.endLat == 0.0 && data.endLng == 0.0) {
            saveDestination(name)
            return
        }

        upsertDestination(name, data.endLng, data.endLat, data.endAddress?.trim().orEmpty())
    }

    suspend fun saveDestinationsFromComponents(components: List<TaskComponent>) {
        if (!preferenceManager.isLocationFootprintEnabled()) return
        components.filterIsInstance<TaskComponent.AMapComponent>().forEach { comp ->
            saveDestinationFromRouteData(comp.data)
        }
    }

    /**
     * 保存单个纯文本目的地
     */
    private suspend fun saveDestination(destination: String) {
        try {
            val routeData = aMapRepository.geocodeLocation(destination)
            if (routeData != null) {
                upsertDestination(
                    name = destination,
                    lng = routeData.endLng,
                    lat = routeData.endLat,
                    address = routeData.endAddress?.trim().orEmpty()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun upsertDestination(
        name: String,
        lng: Double,
        lat: Double,
        address: String = ""
    ) {
        val cleanAddress = address.trim()
        try {
            val all = userLocationDao.getAllLocationsSync()
            val existing = findMatchingLocation(name, lng, lat, all)
            if (existing != null) {
                val hasNewCoords = lng != 0.0 || lat != 0.0
                userLocationDao.insertOrUpdate(
                    existing.copy(
                        latitude = if (hasNewCoords) lat else existing.latitude,
                        longitude = if (hasNewCoords) lng else existing.longitude,
                        address = cleanAddress.ifBlank { existing.address },
                        destinationCount = existing.destinationCount + 1,
                        lastVisitedAt = System.currentTimeMillis()
                    )
                )
            } else {
                userLocationDao.insertOrUpdate(
                    UserLocationEntity(
                        name = name,
                        latitude = lat,
                        longitude = lng,
                        originCount = 0,
                        destinationCount = 1,
                        address = cleanAddress,
                        lastVisitedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CLUSTER_RADIUS_METERS = 300.0
        const val CROSS_CITY_GUARD_METERS = 5_000.0

        fun isGenericLocationName(name: String?): Boolean {
            if (name.isNullOrBlank()) return true
            val trimmed = name.trim()
            return trimmed == "我的位置" || trimmed == "当前位置"
        }

        /**
         * 优先按 300 米物理聚类匹配已有地点；若未命中再按同名兜底，
         * 但若双方均含坐标且相距超过 5km（如跨城市同名商圈），则拒绝误合并。
         */
        internal fun findMatchingLocation(
            name: String,
            lng: Double,
            lat: Double,
            existingLocations: List<UserLocationEntity>
        ): UserLocationEntity? {
            val hasNewCoords = lng != 0.0 || lat != 0.0

            if (hasNewCoords) {
                val withinCluster = existingLocations.mapNotNull { loc ->
                    if (loc.longitude == 0.0 && loc.latitude == 0.0) return@mapNotNull null
                    val dist = GeoCoordinateUtils.distanceMeters(lng, lat, loc.longitude, loc.latitude)
                    if (dist <= CLUSTER_RADIUS_METERS) loc to dist else null
                }
                if (withinCluster.isNotEmpty()) {
                    // 同一 300m 范围内优先匹配同名条目，否则归并到最近点
                    withinCluster.firstOrNull { it.first.name.equals(name, ignoreCase = true) }
                        ?.let { return it.first }
                    return withinCluster.minByOrNull { it.second }?.first
                }
            }

            return existingLocations.firstOrNull { loc ->
                if (!loc.name.equals(name, ignoreCase = true)) return@firstOrNull false
                val hasLocCoords = loc.longitude != 0.0 || loc.latitude != 0.0
                if (!hasNewCoords || !hasLocCoords) {
                    true
                } else {
                    GeoCoordinateUtils.distanceMeters(lng, lat, loc.longitude, loc.latitude) <= CROSS_CITY_GUARD_METERS
                }
            }
        }

        private fun JsonObject.optStr(key: String): String {
            val el = get(key) ?: return ""
            return if (el.isJsonPrimitive) el.asString.trim() else ""
        }

        private fun JsonObject.optObj(key: String): JsonObject? {
            val el = get(key) ?: return null
            return if (el.isJsonObject) el.asJsonObject else null
        }

        private fun JsonObject.optArr(key: String): JsonArray? {
            val el = get(key) ?: return null
            return if (el.isJsonArray) el.asJsonArray else null
        }

        internal fun extractBestResultFromRegeoJson(responseJson: String): ReverseGeoResult {
            return try {
                val root = JsonParser.parseString(responseJson)
                if (!root.isJsonObject) return ReverseGeoResult("当前位置", "")
                val json = root.asJsonObject
                if (json.optStr("status") != "1") return ReverseGeoResult("当前位置", "")
                val regeocode = json.optObj("regeocode") ?: return ReverseGeoResult("当前位置", "")
                ReverseGeoResult(
                    name = extractBestNameFromRegeoObject(regeocode),
                    address = regeocode.optStr("formatted_address")
                )
            } catch (_: Exception) {
                ReverseGeoResult("当前位置", "")
            }
        }

        internal fun extractBestNameFromRegeoJson(responseJson: String): String {
            return extractBestResultFromRegeoJson(responseJson).name
        }

        private fun extractBestNameFromRegeoObject(regeocode: JsonObject): String {
            return try {
                val addressComponent = regeocode.optObj("addressComponent")
                val candidates = mutableListOf<LocationCandidate>()

                // 1. AOI（商圈/校园/小区/园区，最适合保护隐私且具代表性）
                val aois = regeocode.optArr("aois")
                if (aois != null) {
                    for (i in 0 until minOf(3, aois.size())) {
                        val aoi = aois[i]?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                        val aoiName = aoi.optStr("name")
                        val dist = aoi.optStr("distance").toDoubleOrNull()
                        if (aoiName.isNotBlank()) {
                            candidates.add(LocationCandidate(aoiName, CandidateKind.AOI, "", dist))
                        }
                    }
                }

                // 2. neighborhood（高德返回 JsonObject {name,type} 或 空数组 []）
                val nbObj = addressComponent?.optObj("neighborhood")
                val nbName = nbObj?.optStr("name").orEmpty()
                if (nbName.isNotBlank()) {
                    candidates.add(LocationCandidate(nbName, CandidateKind.NEIGHBORHOOD))
                }

                // 3. POI 列表
                val pois = regeocode.optArr("pois")
                if (pois != null) {
                    for (i in 0 until minOf(5, pois.size())) {
                        val poi = pois[i]?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                        val poiName = poi.optStr("name")
                        val poiType = poi.optStr("type")
                        val dist = poi.optStr("distance").toDoubleOrNull()
                        if (poiName.isNotBlank()) {
                            candidates.add(LocationCandidate(poiName, CandidateKind.POI, poiType, dist))
                        }
                    }
                }

                // 4. 街道与区县兜底
                val township = addressComponent?.optStr("township").orEmpty()
                if (township.isNotBlank()) {
                    candidates.add(LocationCandidate(township, CandidateKind.TOWNSHIP))
                }
                val district = addressComponent?.optStr("district").orEmpty()
                if (district.isNotBlank()) {
                    candidates.add(LocationCandidate(district, CandidateKind.DISTRICT))
                }

                pickBestLocationName(candidates)
            } catch (_: Exception) {
                "当前位置"
            }
        }

        internal fun pickBestLocationName(candidates: List<LocationCandidate>): String {
            if (candidates.isEmpty()) return "当前位置"
            val scored = candidates
                .filter { !isGenericLocationName(it.name) }
                .maxByOrNull { scoreCandidate(it) }
                ?: return "当前位置"
            return normalizeCandidateName(scored.name)
        }

        private fun scoreCandidate(candidate: LocationCandidate): Int {
            var score = when (candidate.kind) {
                CandidateKind.AOI -> 100
                CandidateKind.NEIGHBORHOOD -> 90
                CandidateKind.POI -> 70
                CandidateKind.TOWNSHIP -> 35
                CandidateKind.DISTRICT -> 20
            }

            val name = candidate.name
            val type = candidate.poiType

            // 偏好校园/园区/写字楼/商圈/社区等具有代表性的中型地标
            if (listOf("大学", "学院", "校区", "园区", "科技园", "工业园", "广场", "大厦", "小区", "社区", "中心")
                    .any { name.contains(it) }
            ) {
                score += 20
            }
            if (listOf("科教文化", "商务住宅", "购物服务", "风景名胜", "体育休闲").any { type.contains(it) }) {
                score += 10
            }
            // 降权过度细碎的门牌/楼栋/便利店/出入口名称，保护隐私且提升聚类稳定度
            if (Regex("(号楼|栋|单元|室|门$|入口|出口|ATM|公厕|停车场|快递柜)").containsMatchIn(name)) {
                score -= 25
            }
            // 距当前位置过远的 AOI 适当降权
            val dist = candidate.distanceMeters
            if (dist != null && dist > 300.0) {
                score -= 20
            }
            return score
        }

        private fun normalizeCandidateName(raw: String): String {
            return raw.replace(Regex("\\s*\\(.*?\\)"), "")
                .trim()
                .ifBlank { "当前位置" }
        }
    }
}
