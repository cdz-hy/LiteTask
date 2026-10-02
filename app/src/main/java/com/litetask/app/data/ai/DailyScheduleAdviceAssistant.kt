package com.litetask.app.data.ai

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.litetask.app.data.local.DailyScheduleAdviceDao
import com.litetask.app.data.local.FocusSessionDao
import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.UserLocationDao
import com.litetask.app.data.local.UserProfileDao
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.data.repository.AMapRepository
import com.litetask.app.util.CalendarSyncHelper
import com.litetask.app.util.ExternalCalendarEvent
import com.litetask.app.util.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyScheduleAdviceAssistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val focusSessionDao: FocusSessionDao,
    private val userProfileDao: UserProfileDao,
    private val userLocationDao: UserLocationDao,
    private val dailyScheduleAdviceDao: DailyScheduleAdviceDao,
    private val locationProvider: LocationProvider,
    private val aMapRepository: AMapRepository,
    private val preferenceManager: PreferenceManager,
    private val aiProviderFactory: AIProviderFactory
) {
    companion object {
        private const val MAX_AGENT_RETRIES = 12
        private const val RECENT_WINDOW_MS = 14L * 24 * 60 * 60 * 1000L
        private const val ENERGY_WINDOW_MS = 30L * 24 * 60 * 60 * 1000L
        private const val DIRECT_EXTERNAL_TIMEOUT_MS = 3500L

        internal fun matchTravelMode(travelPreference: String?): String {
            val pref = travelPreference?.trim()?.lowercase().orEmpty()
            return when {
                pref.contains("步行") || pref.contains("走路") || pref.contains("walking") -> "walking"
                pref.contains("骑") || pref.contains("单车") || pref.contains("自行车") ||
                    pref.contains("电动") || pref.contains("电瓶") || pref.contains("bicycling") -> "bicycling"
                pref.contains("公交") || pref.contains("地铁") || pref.contains("轨交") ||
                    pref.contains("巴士") || pref.contains("公共交通") || pref.contains("transit") -> "transit"
                else -> "driving"
            }
        }
    }

    private fun canReadCalendar(): Boolean {
        return preferenceManager.isScheduleAdviceUseCalendarEnabled() &&
            CalendarSyncHelper.hasCalendarPermissions(context)
    }

    fun getToolsSchema(
        hasAmapKey: Boolean = !preferenceManager.getAMapKey().isNullOrBlank(),
        hasLocationPermission: Boolean = locationProvider.hasLocationPermission(),
        hasCalendarAccess: Boolean = canReadCalendar()
    ): JSONArray {
        val tools = JSONArray()

        // 1. get_user_profile
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_user_profile")
                put("description", "获取最新用户画像与常用锚定地点，包含完成率、拖延指数、出行偏好、压力水平、偏好沟通语气等。建议首先调用。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 2. get_incomplete_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_incomplete_tasks")
                put("description", "获取未来指定天数内截止的未完成任务列表（含分类、完整起止时间、置顶状态、子任务进度、路线与焦点时长），按置顶与截止时间排序。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("days", JSONObject().apply {
                            put("type", "integer")
                            put("description", "查询未来几天内的任务，1-30，默认7")
                        })
                    })
                })
            })
        })

        // 3. get_overdue_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_overdue_tasks")
                put("description", "获取截止时间已过但尚未完成的逾期任务（含逾期小时数、置顶与子任务进度），用于逾期止损、重排或清理建议。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("description", "最多返回条数，1-50，默认20")
                        })
                    })
                })
            })
        })

        // 4. get_task_details
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_task_details")
                put("description", "获取任务的完整详情（含完整描述、子任务列表、提醒节点、路线与附件组件），用于给出深度可落地建议。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("taskId", JSONObject().apply {
                            put("type", "integer")
                            put("description", "任务ID")
                        })
                    })
                    put("required", JSONArray(listOf("taskId")))
                })
            })
        })

        // 5. find_free_time_slots
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "find_free_time_slots")
                put("description", "综合已有短时段任务与（若开启且授权的）系统日历忙碌日程，识别今明两天>=30分钟的连续空闲时间窗以及潜在时间冲突。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("days", JSONObject().apply {
                            put("type", "integer")
                            put("description", "分析天数，1(仅今天)或2(今明两天)，默认2")
                        })
                    })
                })
            })
        })

        // 6. get_focus_and_energy_pattern
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_focus_and_energy_pattern")
                put("description", "基于近30天专注记录与任务完成时段，分析上午/下午/晚间/深夜四时段精力节律与黄金专注窗口。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 7. search_task_history (统一替换旧的相似任务搜索与历史表现工具)
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_task_history")
                put("description", "统一查询已完成历史任务、真实专注耗时均值、时间跨度与近14天执行趋势，用于合理估时与经验借鉴。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "任务标题或备注关键词；留空则返回近期已完成任务")
                        })
                        put("days", JSONObject().apply {
                            put("type", "integer")
                            put("description", "无关键词时查询近几天已完成任务，1-90，默认30")
                        })
                    })
                })
            })
        })

        // 8. get_previous_schedule_advice
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_previous_schedule_advice")
                put("description", "查看最近1-2次已完成的日程规划建议标题与动作摘要，避免输出重复建议。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 9. get_system_calendar_events (按开关+权限动态注入)
        if (hasCalendarAccess) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_system_calendar_events")
                    put("description", "读取手机系统日历中未来几天的非全天外部忙碌日程（自动过滤>=18小时全天节假日及App自身同步日程）。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("days", JSONObject().apply {
                                put("type", "integer")
                                put("description", "未来天数，1-14，默认3")
                            })
                        })
                    })
                })
            })
        }

        if (hasLocationPermission) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_user_location")
                    put("description", "获取设备当前经纬度坐标，格式 'lng,lat'。")
                    put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
            })
        }

        if (hasAmapKey) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "geocode_location")
                    put("description", "将地名或地址解析为高德经纬度坐标、标准地址与地区编码 adcode。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("address", JSONObject().apply {
                                put("type", "string")
                                put("description", "目的地名称或详细地址")
                            })
                        })
                        put("required", JSONArray(listOf("address")))
                    })
                })
            })

            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "calculate_route")
                    put("description", "调用高德地图计算路径耗时与距离。支持直接传 destination_name 自动地理编码，若省略起点坐标将自动尝试使用当前位置或常用家/公司锚点。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("origin_lng", JSONObject().apply { put("type", "number"); put("description", "起点经度（可选）") })
                            put("origin_lat", JSONObject().apply { put("type", "number"); put("description", "起点纬度（可选）") })
                            put("dest_lng", JSONObject().apply { put("type", "number"); put("description", "终点经度（与 destination_name 二选一）") })
                            put("dest_lat", JSONObject().apply { put("type", "number"); put("description", "终点纬度（与 destination_name 二选一）") })
                            put("destination_name", JSONObject().apply { put("type", "string"); put("description", "终点地名或地址（可替代 dest_lng/dest_lat）") })
                            put("mode", JSONObject().apply {
                                put("type", "string")
                                put("description", "出行方式: driving(驾车), walking(步行), bicycling(骑行), transit(公交)")
                            })
                        })
                    })
                })
            })

            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_weather")
                    put("description", "查询指定地点（或当前位置/常用地点）的多日天气预报与实况气温，适合评估户外出行与雨雪风险。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("location", JSONObject().apply {
                                put("type", "string")
                                put("description", "地名或城市关键词。为空则自动使用当前定位或常用地点。")
                            })
                        })
                    })
                })
            })

            if (hasLocationPermission) {
                tools.put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", "search_nearby_location")
                        put("description", "搜索周边地点，返回名称、地址与坐标。")
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("keyword", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "搜索关键词")
                                })
                            })
                            put("required", JSONArray(listOf("keyword")))
                        })
                    })
                })
            }
        }

        return tools
    }

    private suspend fun loadAnchoredLocations(): List<UserLocationEntity> {
        return userLocationDao.getAllLocationsSync()
            .filter { it.locationType != UserLocationEntity.TYPE_FREQUENT || it.originCount >= 2 }
            .sortedByDescending { it.originCount + it.destinationCount }
            .take(3)
    }

    private suspend fun loadFilteredCalendarEvents(nowMillis: Long, daysAhead: Int): List<ExternalCalendarEvent> {
        if (!canReadCalendar()) return emptyList()
        val zoneId = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val horizonStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val horizonEnd = today.plusDays(daysAhead.toLong().coerceIn(1L, 14L)).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val pulled = CalendarSyncHelper.pullUpcomingEventsFromCalendar(
            context = context,
            daysAhead = daysAhead.coerceIn(1, 14),
            includeReminders = false
        ).getOrElse { emptyList() }
        return ScheduleContextBuilder.filterBusyCalendarEvents(pulled, horizonStart, horizonEnd)
    }

    suspend fun handleToolCall(name: String, args: JSONObject): String {
        return try {
            when (name) {
                "get_user_profile" -> {
                    val profile = userProfileDao.getLatestUserProfile()
                    val anchoredLocations = loadAnchoredLocations()
                    ScheduleAdvicePromptSupport.formatUserProfileJson(profile, anchoredLocations)?.toString()
                        ?: "暂无用户画像与常用地点数据。"
                }
                "get_incomplete_tasks" -> {
                    val days = args.optInt("days", 7).coerceIn(1, 30)
                    val now = System.currentTimeMillis()
                    val endTime = now + days.toLong() * 86_400_000L
                    val composites = taskDao.getIncompleteCompositesInRangeSync(now, endTime)
                    if (composites.isEmpty()) {
                        "未来${days}天内没有未完成的任务。"
                    } else {
                        val array = JsonArray()
                        composites.take(50).forEach { composite ->
                            array.add(ScheduleAdvicePromptSupport.toTaskJson(composite, now, includeDetails = false))
                        }
                        "找到${composites.size}条未完成任务:\n$array"
                    }
                }
                "get_overdue_tasks" -> {
                    val limit = args.optInt("limit", 20).coerceIn(1, 50)
                    val now = System.currentTimeMillis()
                    val overdue = taskDao.getOverdueIncompleteCompositesSync(now, limit)
                    if (overdue.isEmpty()) {
                        "当前没有已逾期的未完成任务。"
                    } else {
                        val array = JsonArray()
                        overdue.forEach { composite ->
                            array.add(ScheduleAdvicePromptSupport.toTaskJson(composite, now, includeDetails = false))
                        }
                        "找到${overdue.size}条已逾期任务:\n$array"
                    }
                }
                "get_task_details" -> {
                    val taskId = args.optLong("taskId")
                    val composite = taskDao.getTaskCompositeByIdSync(taskId)
                    if (composite != null) {
                        ScheduleAdvicePromptSupport.toTaskJson(
                            composite = composite,
                            nowMillis = System.currentTimeMillis(),
                            includeDetails = true
                        ).toString()
                    } else {
                        "任务ID $taskId 不存在。"
                    }
                }
                "find_free_time_slots" -> {
                    val days = args.optInt("days", 2).coerceIn(1, 2)
                    val now = System.currentTimeMillis()
                    val zoneId = ZoneId.systemDefault()
                    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
                    val endOfHorizon = today.plusDays(days.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli() + 8 * 3600_000L
                    val composites = taskDao.getIncompleteCompositesInRangeSync(now, endOfHorizon)
                    val calendarEvents = loadFilteredCalendarEvents(now, days)
                    val analysis = ScheduleContextBuilder.calculateTimeSlots(now, composites, calendarEvents, zoneId)
                    val json = analysis.toJson(zoneId)
                    if (days == 1) {
                        json.remove("free_slots_tomorrow")
                    }
                    json.toString()
                }
                "get_focus_and_energy_pattern" -> {
                    val now = System.currentTimeMillis()
                    val since = now - ENERGY_WINDOW_MS
                    val sessions = focusSessionDao.getSessionsSinceSync(since)
                    val completed = taskDao.getCompletedTaskCompositesSinceSync(since, 100)
                    ScheduleContextBuilder.analyzeFocusEnergyPattern(sessions, completed).toJson().toString()
                }
                "search_task_history", "search_completed_similar_tasks", "get_past_task_performance" -> {
                    handleUnifiedTaskHistory(args)
                }
                "get_previous_schedule_advice" -> {
                    val recent = dailyScheduleAdviceDao.getRecentCompletedSync(2)
                    ScheduleContextBuilder.buildPreviousAdviceSummaryJson(recent)
                }
                "get_system_calendar_events" -> {
                    if (!canReadCalendar()) {
                        return "系统日历读取未启用或未授予日历权限。"
                    }
                    val days = args.optInt("days", 3).coerceIn(1, 14)
                    val now = System.currentTimeMillis()
                    val events = loadFilteredCalendarEvents(now, days)
                    if (events.isEmpty()) {
                        "未来${days}天系统日历中没有非全天的外部忙碌日程。"
                    } else {
                        val arr = JsonArray()
                        events.take(30).forEach { ev ->
                            arr.add(JsonObject().apply {
                                addProperty("title", ev.title)
                                addProperty("start", ScheduleAdvicePromptSupport.formatDateTime(ev.startTime))
                                addProperty("end", ScheduleAdvicePromptSupport.formatDateTime(ev.deadline))
                                ev.location?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("location", it) }
                            })
                        }
                        "找到${events.size}条系统日历日程:\n$arr"
                    }
                }
                "get_user_location" -> {
                    val location = locationProvider.getCurrentLocation()
                    if (location != null) {
                        "${location.longitude},${location.latitude}"
                    } else {
                        if (!locationProvider.hasLocationPermission()) "无法获取位置：缺少定位权限。"
                        else "无法获取位置：定位服务可能未开启。"
                    }
                }
                "geocode_location" -> {
                    val address = args.optString("address", "").trim()
                    if (address.isBlank()) return "请提供地址或地名。"
                    val geo = aMapRepository.geocodeLocation(address)
                    if (geo != null) {
                        JsonObject().apply {
                            addProperty("name", geo.endName)
                            if (!geo.endAddress.isNullOrBlank()) addProperty("formatted_address", geo.endAddress)
                            addProperty("lng", geo.endLng)
                            addProperty("lat", geo.endLat)
                            if (!geo.adcode.isNullOrBlank()) addProperty("adcode", geo.adcode)
                        }.toString()
                    } else {
                        "无法解析地点'$address'的坐标，请检查高德API Key或地名。"
                    }
                }
                "calculate_route" -> {
                    handleCalculateRouteTool(args)
                }
                "get_weather" -> {
                    handleWeatherTool(args)
                }
                "search_nearby_location" -> {
                    val keyword = args.optString("keyword", "")
                    val loc = locationProvider.getCurrentLocation()
                    if (loc == null) return "无法获取当前位置。"
                    val results = aMapRepository.searchNearby(keyword, "${loc.longitude},${loc.latitude}", 50000)
                    if (results.isEmpty()) {
                        "未找到'$keyword'相关地点。"
                    } else {
                        val sb = StringBuilder()
                        results.take(3).forEachIndexed { i, r ->
                            sb.appendLine("${i + 1}. ${r.name} (${r.address ?: "无详细地址"}) 坐标:${r.lng},${r.lat}")
                        }
                        sb.toString()
                    }
                }
                else -> "未知工具: $name"
            }
        } catch (e: Exception) {
            "工具调用失败: ${e.message ?: "未知错误"}"
        }
    }

    private suspend fun handleUnifiedTaskHistory(args: JSONObject): String {
        val keyword = args.optString("keyword", "").trim()
        val days = args.optInt("days", 30).coerceIn(1, 90)
        val now = System.currentTimeMillis()
        val composites = if (keyword.isNotEmpty()) {
            taskDao.searchCompletedCompositesSync(keyword, 20)
        } else {
            taskDao.getCompletedTaskCompositesSinceSync(now - days.toLong() * 86_400_000L, 20)
        }
        val recentSessions = focusSessionDao.getSessionsSinceSync(now - RECENT_WINDOW_MS)
        val recentFocusSummary = if (recentSessions.isNotEmpty()) {
            val totalMin = recentSessions.sumOf { it.actualSeconds } / 60L
            val avgSessionMin = totalMin.toDouble() / recentSessions.size
            "近14天累计专注${recentSessions.size}次（共${totalMin}分钟，单次均值${String.format(Locale.US, "%.1f", avgSessionMin)}分钟）。"
        } else "近14天暂无专注计时记录。"

        if (composites.isEmpty()) {
            val label = if (keyword.isNotEmpty()) "未找到与'$keyword'相关的已完成任务。" else "近${days}天暂无已完成任务。"
            return "$label $recentFocusSummary"
        }

        val tasks = composites.map { it.task }
        val focusTasks = tasks.filter { it.totalFocusSeconds > 0L }
        val realFocusPart = if (focusTasks.isNotEmpty()) {
            val avgFocusMin = focusTasks.map { it.totalFocusSeconds / 60.0 }.average()
            "${focusTasks.size}条含专注记录的任务实际专注均值${String.format(Locale.US, "%.1f", avgFocusMin)}分钟。"
        } else ""
        val durations = tasks.mapNotNull { t ->
            t.completedAt?.minus(t.startTime)?.takeIf { it > 0L }
        }
        val spanPart = if (durations.isNotEmpty()) {
            "计划起始至完成跨度均值${String.format(Locale.US, "%.1f", durations.average() / 3_600_000.0)}小时。"
        } else ""

        val sampleArr = JsonArray()
        composites.take(10).forEach { c ->
            val t = c.task
            sampleArr.add(JsonObject().apply {
                addProperty("id", t.id)
                addProperty("title", t.title)
                addProperty("category", c.category?.name ?: "默认")
                addProperty("completed_at", ScheduleAdvicePromptSupport.formatDateTime(t.completedAt ?: 0L))
                if (t.totalFocusSeconds > 0L) {
                    addProperty("focused_minutes", t.totalFocusSeconds / 60L)
                }
            })
        }
        val statsSummary = listOf(realFocusPart, spanPart, recentFocusSummary)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return "找到${composites.size}条已完成任务（$statsSummary）\n$sampleArr"
    }

    private suspend fun handleCalculateRouteTool(args: JSONObject): String {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return "未配置高德地图API Key。"

        var originLng = args.optDouble("origin_lng", 0.0).takeIf { !it.isNaN() } ?: 0.0
        var originLat = args.optDouble("origin_lat", 0.0).takeIf { !it.isNaN() } ?: 0.0
        var originNote = "指定起点"

        if (originLng == 0.0 || originLat == 0.0) {
            val loc = if (locationProvider.hasLocationPermission()) locationProvider.getCurrentLocation() else null
            if (loc != null) {
                originLng = loc.longitude
                originLat = loc.latitude
                originNote = "当前位置"
            } else {
                val anchor = loadAnchoredLocations().firstOrNull { it.longitude != 0.0 && it.latitude != 0.0 }
                if (anchor != null) {
                    originLng = anchor.longitude
                    originLat = anchor.latitude
                    originNote = "常用地点(${anchor.name})"
                }
            }
        }
        if (originLng == 0.0 || originLat == 0.0) {
            return "无法确定起点坐标（请开启定位权限、补充常用地点或传入 origin_lng/origin_lat）。"
        }

        var destLng = args.optDouble("dest_lng", 0.0).takeIf { !it.isNaN() } ?: 0.0
        var destLat = args.optDouble("dest_lat", 0.0).takeIf { !it.isNaN() } ?: 0.0
        val destName = args.optString("destination_name", "").trim()
        if ((destLng == 0.0 || destLat == 0.0) && destName.isNotBlank() && !destName.equals("null", ignoreCase = true)) {
            val geo = aMapRepository.geocodeLocation(destName)
                ?: return "无法地理编码目的地'$destName'。"
            destLng = geo.endLng
            destLat = geo.endLat
        }
        if (destLng == 0.0 || destLat == 0.0) {
            return "终点坐标与 destination_name 均不完整。"
        }

        val userProfile = userProfileDao.getLatestUserProfile()
        val defaultMode = matchTravelMode(userProfile?.travelPreference)
        val rawMode = args.optString("mode", "").trim()
        val mode = if (rawMode.isNotBlank() && !rawMode.equals("null", ignoreCase = true)) {
            rawMode.lowercase()
        } else {
            defaultMode
        }
        val result = aMapRepository.calculateRoute(originLng, originLat, destLng, destLat, mode)
        return if (result != null) {
            val distKm = String.format(Locale.US, "%.1f", result.second / 1000.0)
            "从${originNote}出发，采用${mode}模式，约${result.first / 60}分钟，距离${distKm}公里。"
        } else {
            "路线查询失败，请检查高德API Key或参数。"
        }
    }

    private suspend fun resolveWeatherAdcode(locationArg: String): String? {
        if (locationArg.isNotBlank() && !locationArg.equals("null", ignoreCase = true)) {
            return aMapRepository.geocodeLocation(locationArg)?.adcode
        }
        if (locationProvider.hasLocationPermission()) {
            val loc = locationProvider.getCurrentLocation()
            if (loc != null) {
                aMapRepository.reverseGeocodeAdcode(loc.longitude, loc.latitude)?.let { return it }
            }
        }
        val anchor = loadAnchoredLocations().firstOrNull()
        if (anchor != null) {
            if (anchor.longitude != 0.0 && anchor.latitude != 0.0) {
                aMapRepository.reverseGeocodeAdcode(anchor.longitude, anchor.latitude)?.let { return it }
            }
            val queryName = anchor.address.takeIf { it.isNotBlank() } ?: anchor.name
            if (queryName.isNotBlank()) {
                return aMapRepository.geocodeLocation(queryName)?.adcode
            }
        }
        return null
    }

    private suspend fun handleWeatherTool(args: JSONObject): String {
        val key = preferenceManager.getAMapKey()
        if (key.isNullOrBlank()) return "未配置高德地图API Key。"
        val locationArg = args.optString("location", "").trim()
        val adcode = resolveWeatherAdcode(locationArg)
            ?: return "无法获取目标地区编码（可尝试指定城市或地名，如“北京市海淀区”）。"
        val forecast = aMapRepository.fetchWeatherForecast(adcode)
        if (forecast != null) {
            return ScheduleContextBuilder.formatWeatherForecastText(forecast)
        }
        val live = aMapRepository.fetchWeather(adcode)
        return if (live != null) {
            "天气实况: ${live.first}, 温度: ${live.second}°C"
        } else {
            "天气查询失败。"
        }
    }

    suspend fun generateAdvice(
        onProgress: (String) -> Unit
    ): Result<DailyScheduleAdviceEntity> {
        val providerId = preferenceManager.getAiProvider()
        val apiKey = preferenceManager.getApiKey(providerId).orEmpty()
        preferenceManager.getAiConfigError(apiKey)?.let { errorMsg ->
            return Result.failure(Exception(errorMsg))
        }

        val provider = aiProviderFactory.getProvider(providerId)
        val modelId = preferenceManager.getAiModel()
        val now = System.currentTimeMillis()
        val currentDate = ScheduleAdvicePromptSupport.formatHeaderNow(now)
        val hasAmapKey = !preferenceManager.getAMapKey().isNullOrBlank()
        val hasLocationPermission = locationProvider.hasLocationPermission()
        val hasCalendarAccess = canReadCalendar()
        val profileTone = userProfileDao.getLatestUserProfile()?.aiTone

        val systemPrompt = ScheduleAdvicePromptSupport.buildAgentSystemPrompt(
            currentDate = currentDate,
            hasAmapKey = hasAmapKey,
            hasLocationPermission = hasLocationPermission,
            hasCalendarAccess = hasCalendarAccess,
            toneHint = profileTone
        )

        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", "请为我分析当前待办与逾期任务，生成今日日程规划建议。") })
        }

        val tools = getToolsSchema(
            hasAmapKey = hasAmapKey,
            hasLocationPermission = hasLocationPermission,
            hasCalendarAccess = hasCalendarAccess
        )
        var retryCount = 0

        onProgress("正在初始化日程分析...")

        while (retryCount < MAX_AGENT_RETRIES) {
            val isForcedFinalRound = retryCount == MAX_AGENT_RETRIES - 1
            if (isForcedFinalRound) {
                onProgress("工具收集完成，正在汇总最终建议...")
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", "数据采集已达到上限，请立刻结合以上所有信息，严格按要求直接输出最终纯 JSON 日程建议。")
                })
            }

            val response = provider.chatWithTools(
                apiKey = apiKey,
                model = modelId,
                messages = messages,
                tools = if (isForcedFinalRound) null else tools
            )
            if (response.isFailure) return Result.failure(response.exceptionOrNull()!!)

            val responseJson = response.getOrNull()
                ?: return Result.failure(Exception("AI 响应为空"))
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(Exception("AI 响应格式异常"))
            }
            val choice = choices.optJSONObject(0)
            val message = choice?.optJSONObject("message")
                ?: return Result.failure(Exception("AI 未返回有效消息"))

            val toolCallsArray = if (!isForcedFinalRound && message.has("tool_calls") && !message.isNull("tool_calls")) {
                message.optJSONArray("tool_calls")
            } else null

            if (toolCallsArray != null && toolCallsArray.length() > 0) {
                messages.put(message)

                val reasoning = message.optString("reasoning_content", "").takeIf { it.isNotBlank() && it != "null" }
                    ?: message.optString("content", "").takeIf { it.isNotBlank() && it != "null" }
                    ?: ""
                if (reasoning.isNotBlank()) onProgress(reasoning)

                coroutineScope {
                    val jobs = (0 until toolCallsArray.length()).map { i ->
                        val call = toolCallsArray.getJSONObject(i)
                        val function = call.getJSONObject("function")
                        val name = function.getString("name")
                        val arguments = try {
                            JSONObject(function.optString("arguments", "{}"))
                        } catch (_: Exception) {
                            JSONObject()
                        }
                        val callId = call.getString("id")

                        onProgress(progressLabelForTool(name))

                        async {
                            val result = handleToolCall(name, arguments)
                            val summary = summaryLabelForTool(name, result)
                            onProgress(summary)
                            callId to result
                        }
                    }
                    jobs.forEach { deferred ->
                        val (callId, result) = deferred.await()
                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", callId)
                            put("content", result)
                        })
                    }
                }
                retryCount++
            } else {
                val finalContent = message.optString("content", "")
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?: return Result.failure(Exception("AI 未生成正文建议"))
                onProgress("正在整理建议...")
                return parseAdviceOutput(finalContent)
            }
        }
        return Result.failure(Exception("分析轮次过多，请重试"))
    }

    private fun progressLabelForTool(name: String): String = when (name) {
        "get_user_profile" -> "正在读取用户画像..."
        "get_incomplete_tasks" -> "正在获取近期未完成任务..."
        "get_overdue_tasks" -> "正在检查已逾期任务..."
        "get_task_details" -> "正在查阅任务详情..."
        "find_free_time_slots" -> "正在计算今明可用空闲时段..."
        "get_focus_and_energy_pattern" -> "正在分析专注与精力节律..."
        "search_task_history", "search_completed_similar_tasks", "get_past_task_performance" -> "正在检索历史完成与专注记录..."
        "get_previous_schedule_advice" -> "正在参考上次规划摘要..."
        "get_system_calendar_events" -> "正在读取系统日历占用..."
        "get_user_location" -> "正在获取当前位置..."
        "geocode_location" -> "正在解析地点坐标..."
        "calculate_route" -> "正在计算路线耗时..."
        "get_weather" -> "正在查询天气预报..."
        "search_nearby_location" -> "正在搜索周边地点..."
        else -> "正在调用 $name..."
    }

    private fun summaryLabelForTool(name: String, result: String): String = when (name) {
        "get_user_profile" -> "已获取用户画像数据"
        "get_incomplete_tasks" -> {
            val count = extractCountPrefix(result)
            if (count != null) "获取到${count}条未完成任务" else "已获取未完成任务列表"
        }
        "get_overdue_tasks" -> {
            val count = extractCountPrefix(result)
            if (count != null) "发现${count}条已逾期任务" else "已检查逾期任务"
        }
        "get_task_details" -> "已查阅任务详情"
        "find_free_time_slots" -> "已识别空闲时间与冲突"
        "get_focus_and_energy_pattern" -> "精力节律分析完成"
        "search_task_history", "search_completed_similar_tasks", "get_past_task_performance" -> "历史执行效率分析完成"
        "get_previous_schedule_advice" -> "已读取近期历史规划"
        "get_system_calendar_events" -> {
            val count = extractCountPrefix(result)
            if (count != null) "读取到${count}条系统日历日程" else "系统日历检查完成"
        }
        "get_user_location" -> "已获取当前位置"
        "geocode_location" -> "地点坐标解析完成"
        "calculate_route" -> "路线耗时计算完成"
        "get_weather" -> "天气预报获取完成"
        "search_nearby_location" -> "地点搜索完成"
        else -> "已调用 $name"
    }

    private fun extractCountPrefix(result: String): Int? =
        result.lines().firstOrNull()?.replace("找到", "")?.substringBefore("条")?.toIntOrNull()

    private fun parseAdviceOutput(content: String): Result<DailyScheduleAdviceEntity> {
        return ScheduleAdvicePromptSupport.parseAdviceJson(content).map { (shortTerm, longTerm) ->
            DailyScheduleAdviceEntity(
                shortTermJson = shortTerm,
                longTermJson = longTerm,
                generationStatus = "COMPLETED"
            )
        }
    }

    suspend fun generateDirectAdvice(
        onProgress: (String) -> Unit
    ): Result<DailyScheduleAdviceEntity> {
        val providerId = preferenceManager.getAiProvider()
        val apiKey = preferenceManager.getApiKey(providerId).orEmpty()
        preferenceManager.getAiConfigError(apiKey)?.let { errorMsg ->
            return Result.failure(Exception(errorMsg))
        }

        onProgress("正在采集画像、任务、日程与精力节律...")
        val now = System.currentTimeMillis()
        val since14d = now - RECENT_WINDOW_MS
        val since30d = now - ENERGY_WINDOW_MS
        val hasAmapKey = !preferenceManager.getAMapKey().isNullOrBlank()
        val readCalendar = canReadCalendar()

        val snapshot = coroutineScope {
            val profileDef = async { userProfileDao.getLatestUserProfile() }
            val locsDef = async { loadAnchoredLocations() }
            val upcomingDef = async { taskDao.getIncompleteCompositesInRangeSync(now, now + 7 * 86_400_000L).take(35) }
            val overdueDef = async { taskDao.getOverdueIncompleteCompositesSync(now, 15) }
            val sessionsDef = async { focusSessionDao.getSessionsSinceSync(since30d) }
            val completedDef = async { taskDao.getCompletedTaskCompositesSinceSync(since30d, 60) }
            val calendarDef = async {
                if (readCalendar) {
                    withTimeoutOrNull(DIRECT_EXTERNAL_TIMEOUT_MS) { loadFilteredCalendarEvents(now, 3) }.orEmpty()
                } else emptyList()
            }
            val weatherDef = async {
                if (hasAmapKey) {
                    withTimeoutOrNull(DIRECT_EXTERNAL_TIMEOUT_MS) {
                        resolveWeatherAdcode("")?.let { adcode ->
                            aMapRepository.fetchWeatherForecast(adcode)
                                ?.let { ScheduleContextBuilder.formatWeatherForecastText(it) }
                                ?: aMapRepository.fetchWeather(adcode)?.let { "${it.first} ${it.second}°C" }
                        }.orEmpty()
                    }.orEmpty()
                } else ""
            }
            DirectAdviceSnapshot(
                profile = profileDef.await(),
                anchoredLocations = locsDef.await(),
                upcoming = upcomingDef.await(),
                overdue = overdueDef.await(),
                sessions30d = sessionsDef.await(),
                completedRecent30d = completedDef.await(),
                calendarEvents = calendarDef.await(),
                weatherText = weatherDef.await()
            )
        }

        val recentSessions14d = snapshot.sessions30d.filter { it.startedAt >= since14d }
        val recentCompleted14d = snapshot.completedRecent30d.filter { (it.task.completedAt ?: 0L) >= since14d }
        val timeSlots = ScheduleContextBuilder.calculateTimeSlots(
            nowMillis = now,
            composites = snapshot.upcoming,
            externalEvents = snapshot.calendarEvents
        )
        val focusEnergy = ScheduleContextBuilder.analyzeFocusEnergyPattern(
            sessions = snapshot.sessions30d,
            completedTasks = snapshot.completedRecent30d
        )

        val currentDate = ScheduleAdvicePromptSupport.formatHeaderNow(now)
        val prompt = ScheduleAdvicePromptSupport.buildDirectUserPrompt(
            currentDate = currentDate,
            profileText = ScheduleAdvicePromptSupport.formatProfileSummaryText(snapshot.profile, snapshot.anchoredLocations),
            tasksText = ScheduleAdvicePromptSupport.formatTasksForDirectPrompt(snapshot.upcoming, snapshot.overdue, now),
            recentFocusText = ScheduleAdvicePromptSupport.formatRecentFocusSummaryText(recentSessions14d, recentCompleted14d),
            toneHint = snapshot.profile?.aiTone,
            calendarContextText = ScheduleContextBuilder.formatCalendarEventsForPrompt(snapshot.calendarEvents),
            freeSlotsText = timeSlots.toPromptSummary(),
            focusEnergyText = focusEnergy.toPromptSummary(),
            weatherText = snapshot.weatherText
        )

        onProgress("正在生成今日日程规划...")

        val provider = aiProviderFactory.getProvider(providerId)
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "你是 LiteTask 首席日程规划官。严格遵循输出约束，直接输出纯 JSON，不要包含 Markdown 代码块。")
            })
            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
        }

        return try {
            val result = provider.chatWithTools(apiKey, preferenceManager.getAiModel(), messages, null)
            if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
            val responseJson = result.getOrNull()
                ?: return Result.failure(Exception("AI 响应为空"))
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(Exception("AI 响应格式异常"))
            }
            val content = choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                ?: return Result.failure(Exception("AI 响应为空"))
            parseAdviceOutput(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class DirectAdviceSnapshot(
        val profile: com.litetask.app.data.model.UserProfileHistoryEntity?,
        val anchoredLocations: List<UserLocationEntity>,
        val upcoming: List<TaskDetailComposite>,
        val overdue: List<TaskDetailComposite>,
        val sessions30d: List<com.litetask.app.data.model.FocusSessionEntity>,
        val completedRecent30d: List<TaskDetailComposite>,
        val calendarEvents: List<ExternalCalendarEvent>,
        val weatherText: String
    )
}
