package com.litetask.app.data.ai

import com.litetask.app.data.local.TaskDao
import com.litetask.app.data.local.UserProfileDao
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.data.repository.AMapRepository
import com.litetask.app.data.repository.CategoryRepository
import com.litetask.app.util.LocationProvider
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyScheduleAdviceAssistant @Inject constructor(
    private val taskDao: TaskDao,
    private val userProfileDao: UserProfileDao,
    private val locationProvider: LocationProvider,
    private val aMapRepository: AMapRepository,
    private val preferenceManager: PreferenceManager,
    private val aiProviderFactory: AIProviderFactory,
    private val categoryRepository: CategoryRepository
) {
    companion object {
        private const val MAX_AGENT_RETRIES = 12
    }

    fun getToolsSchema(): JSONArray {
        val tools = JSONArray()

        // 1. get_user_profile
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_user_profile")
                put("description", "获取最新用户画像，包含完成率、拖延指数、出行偏好、压力水平以及主观标签。建议在分析前首先调用。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 2. get_incomplete_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_incomplete_tasks")
                put("description", "获取未来指定天数内截止的未完成任务列表（按截止时间升序）。")
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

        // 3. get_task_details
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_task_details")
                put("description", "获取任务的完整详情（含描述、子任务等），用于给出深度建议。")
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

        // 4. search_completed_similar_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_completed_similar_tasks")
                put("description", "搜索已完成的相似任务，用于参考历史完成情况。")
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

        // 5. get_user_location
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_user_location")
                put("description", "获取设备当前经纬度坐标，格式 'lng,lat'。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 6. calculate_route
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "calculate_route")
                put("description", "调用高德地图API计算路径规划，返回耗时(秒)和距离(米)。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("origin_lng", JSONObject().apply { put("type", "number"); put("description", "起点经度") })
                        put("origin_lat", JSONObject().apply { put("type", "number"); put("description", "起点纬度") })
                        put("dest_lng", JSONObject().apply { put("type", "number"); put("description", "终点经度") })
                        put("dest_lat", JSONObject().apply { put("type", "number"); put("description", "终点纬度") })
                        put("mode", JSONObject().apply { 
                            put("type", "string") 
                            put("description", "出行方式: driving(驾车), walking(步行), bicycling(骑行), transit(公交)") 
                        })
                    })
                    put("required", JSONArray(listOf("origin_lng", "origin_lat", "dest_lng", "dest_lat")))
                })
            })
        })

        // 7. get_weather
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_weather")
                put("description", "查询天气实况。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("location", JSONObject().apply {
                            put("type", "string")
                            put("description", "地名关键词。为空则使用当前位置。")
                        })
                    })
                })
            })
        })

        // 8. search_nearby_location
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_nearby_location")
                put("description", "搜索周边地点，返回坐标。")
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

        // 9. get_categories
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_categories")
                put("description", "获取所有任务分类。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })
        
        // 10. get_past_task_performance
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_past_task_performance")
                put("description", "查询过去类似任务的执行效率和平均耗时。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "任务关键词")
                        })
                    })
                })
            })
        })

        return tools
    }

    suspend fun handleToolCall(name: String, args: JSONObject): String {
        return try {
        when (name) {
            "get_user_profile" -> {
                val profile = userProfileDao.getLatestUserProfile()
                if (profile != null) {
                    JSONObject().apply {
                        put("completionRate", profile.completionRate)
                        put("delayedRate", profile.delayedRate)
                        put("identity", profile.identity ?: "")
                        put("industry", profile.industry ?: "")
                        put("travelPreference", profile.travelPreference ?: "")
                        put("delayIndex", profile.delayIndex)
                        put("planAbility", profile.planAbility)
                        put("stressLevel", profile.stressLevel)
                        put("executionRhythm", profile.executionRhythm ?: "")
                        put("personality", profile.personality ?: "")
                    }.toString()
                } else {
                    "暂无用户画像数据。"
                }
            }
            "get_incomplete_tasks" -> {
                val days = args.optInt("days", 7).coerceIn(1, 30)
                val now = System.currentTimeMillis()
                val endTime = now + days.toLong() * 86400000L
                val tasks = taskDao.getIncompleteTasksInRange(now, endTime)
                if (tasks.isEmpty()) {
                    "未来${days}天内没有未完成的任务。"
                } else {
                    val array = JSONArray()
                    tasks.take(50).forEach { t ->
                        array.put(JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("deadline", SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t.deadline)))
                            put("is_expired", t.isExpired)
                        })
                    }
                    "找到${tasks.size}条任务:\n$array"
                }
            }
            "get_task_details" -> {
                val taskId = args.optLong("taskId")
                val composite = taskDao.getTaskDetailCompositeSync(taskId)
                if (composite != null) {
                    val t = composite.task
                    JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("description", t.description)
                        put("category", composite.category?.name ?: "默认")
                        put("startTime", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(t.startTime)))
                        put("deadline", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(t.deadline)))
                        put("is_done", t.isDone)
                        put("is_expired", t.isExpired)
                        if (composite.subTasks.isNotEmpty()) {
                            put("sub_tasks", JSONArray().apply {
                                composite.subTasks.forEach { st ->
                                    put(JSONObject().apply {
                                        put("content", st.content)
                                        put("is_completed", st.isCompleted)
                                    })
                                }
                            })
                        }
                    }.toString()
                } else {
                    "任务ID $taskId 不存在。"
                }
            }
            "search_completed_similar_tasks" -> {
                val keyword = args.optString("keyword")
                val tasks = taskDao.searchCompletedTasks(keyword, 10)
                if (tasks.isEmpty()) {
                    "未找到与'$keyword'相关的已完成任务。"
                } else {
                    val array = JSONArray()
                    tasks.forEach { t ->
                        array.put(JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("completed_at", SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t.completedAt ?: 0)))
                        })
                    }
                    "找到${tasks.size}条已完成的相似任务:\n$array"
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
            "calculate_route" -> {
                val originLng = args.optDouble("origin_lng")
                val originLat = args.optDouble("origin_lat")
                val destLng = args.optDouble("dest_lng")
                val destLat = args.optDouble("dest_lat")
                val userProfile = userProfileDao.getLatestUserProfile()
                val defaultMode = when (userProfile?.travelPreference?.lowercase()) {
                    "步行" -> "walking"
                    "骑行" -> "bicycling"
                    "公交" -> "transit"
                    else -> "driving"
                }
                val mode = args.optString("mode", defaultMode)
                if (originLng == 0.0 || originLat == 0.0 || destLng == 0.0 || destLat == 0.0) {
                    return "坐标参数不完整。"
                }
                val result = aMapRepository.calculateRoute(originLng, originLat, destLng, destLat, mode)
                if (result != null) {
                    "采用${mode}模式，约${result.first / 60}分钟，距离${result.second / 1000.0}公里。"
                } else {
                    "路线查询失败，请检查高德API Key或参数。"
                }
            }
            "get_weather" -> {
                val key = preferenceManager.getAMapKey()
                if (key.isNullOrBlank()) return "未配置高德地图API Key。"
                val locationArg = args.optString("location", "")
                val adcode = if (locationArg.isNotBlank()) {
                    val geoResult = aMapRepository.geocodeLocation(locationArg)
                    geoResult?.adcode
                } else {
                    val loc = locationProvider.getCurrentLocation()
                    if (loc != null) {
                        val nearby = aMapRepository.searchNearby("建筑", "${loc.longitude},${loc.latitude}", 1000)
                        nearby.firstOrNull()?.adcode
                    } else null
                }
                if (adcode.isNullOrBlank()) return "无法获取地区编码。"
                val weather = aMapRepository.fetchWeather(adcode)
                if (weather != null) {
                    "天气: ${weather.first}, 温度: ${weather.second}°C"
                } else {
                    "天气查询失败。"
                }
            }
            "search_nearby_location" -> {
                val keyword = args.optString("keyword")
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
            "get_categories" -> {
                val categories = categoryRepository.getAllCategoriesSync()
                categories.joinToString(", ") { "${it.name}(ID:${it.id})" }
            }
            "get_past_task_performance" -> {
                val keyword = args.optString("keyword")
                val tasks = taskDao.searchCompletedTasks(keyword, 20)
                if (tasks.isEmpty()) "暂无相关历史任务数据。"
                else {
                    val durations = tasks.mapNotNull { t -> t.completedAt?.minus(t.startTime) }
                    if (durations.isEmpty()) "暂无足够有效的时长数据。"
                    else {
                        val avgTime = durations.average()
                        "过去类似任务平均耗时: ${String.format("%.1f", avgTime / 3600000.0)}小时。"
                    }
                }
            }
            else -> "未知工具: $name"
        }
        } catch (e: Exception) {
            "工具调用失败: ${e.message ?: "未知错误"}"
        }
    }

    suspend fun generateAdvice(
        onProgress: (String) -> Unit
    ): Result<DailyScheduleAdviceEntity> {
        val apiKey = preferenceManager.getApiKey()
        if (apiKey.isNullOrBlank()) return Result.failure(Exception("未配置API Key"))

        val providerId = preferenceManager.getAiProvider()
        val provider = aiProviderFactory.getProvider(providerId)
            ?: return Result.failure(Exception("当前 AI 提供商不支持日程建议"))
        val modelId = preferenceManager.getAiModel()

        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.CHINESE).format(Date())
        val systemPrompt = """
            # Role: LiteTask 首席日程规划官 (当前时间: $currentDate)

            你是一位专业的日程管理专家，负责为用户提供极其清晰、专业且具有行动力的建议。

            # 核心指令
            1. **禁止使用 Emoji**: 严禁在输出中出现任何表情符号。
            2. **隐藏 ID**: 严禁在建议内容中直接提及任务 ID。必须使用任务的【标题】来引用相关任务。
            3. **任务关联**: 每条建议必须明确指向一个或多个具体的任务标题。
            4. **简洁有序**: 建议应直接、专业，避免啰嗦和复杂的背景描述。
            5. **画像整合**: 必须参考用户画像中的拖延指数、压力水平、出行偏好（驾车/步行等）进行规划。
            6. **时效校验**: 获取任务后必须检查截止日期是否合理（与当前时间对比），已完成任务仅作历史参考，不应出现在执行建议中。

            # 工作流程
            - 获取用户画像 (get_user_profile)。
            - 获取近期任务 (get_incomplete_tasks)，默认7天。
            - 对重点任务查询详情 (get_task_details)。
            - 画像中的 travelPreference 即为用户常用出行方式，calculate_route 的 mode 参数应优先使用该值。
            - 考虑环境因素 (get_user_location, get_weather, calculate_route)。
            - 输出专业规划。

            # 输出格式 (必须是纯 JSON)
            {
              "short_term": [
                {
                  "task_title": "任务标题或统筹名称",
                  "advice_type": "建议类型(如: 执行建议、出行规划、统筹安排)",
                  "content": "具体的建议内容，描述要清晰且专业",
                  "info": "补充信息(如: 预计耗时30分钟，傍晚有雨)"
                }
              ],
              "long_term": [
                {
                  "topic": "关注任务或领域",
                  "advice_type": "规划类型(如: 风险预警、长期规划、习惯建议)",
                  "content": "建议内容",
                  "urgency": "轻重缓急(高、中、低)"
                }
              ]
            }
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", "请为我生成今日日程建议。") })
        }

        val tools = getToolsSchema()
        var retryCount = 0

        onProgress("正在初始化日程分析...")

        while (retryCount < MAX_AGENT_RETRIES) {
            val response = provider.chatWithTools(apiKey, modelId, messages, tools)
            if (response.isFailure) return Result.failure(response.exceptionOrNull()!!)

            val responseJson = response.getOrNull()
                ?: return Result.failure(Exception("AI 响应为空"))
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(Exception("AI 响应格式异常"))
            }
            val choice = choices.optJSONObject(0)
            val message = choice?.optJSONObject("message") ?: break

            if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                messages.put(message)

                val reasoning = if (message.has("reasoning_content") && !message.isNull("reasoning_content")) {
                    message.optString("reasoning_content")
                } else {
                    message.optString("content")
                }
                if (reasoning.isNotBlank()) onProgress(reasoning)

                val toolCalls = message.getJSONArray("tool_calls")
                // 并行执行所有工具调用，同时保持上下文感知
                coroutineScope {
                    val jobs = (0 until toolCalls.length()).map { i ->
                        val call = toolCalls.getJSONObject(i)
                        val function = call.getJSONObject("function")
                        val name = function.getString("name")
                        val arguments = try { JSONObject(function.getString("arguments")) } catch (e: Exception) { JSONObject() }
                        val callId = call.getString("id")

                        val progressMsg = when (name) {
                            "get_user_profile" -> "正在读取用户画像..."
                            "get_incomplete_tasks" -> "正在获取近期任务..."
                            "get_task_details" -> "正在查阅任务详情..."
                            "search_completed_similar_tasks" -> "正在检索历史相似任务..."
                            "get_user_location" -> "正在获取当前位置..."
                            "calculate_route" -> "正在计算路线耗时..."
                            "get_weather" -> "正在查询天气..."
                            "search_nearby_location" -> "正在搜索地点..."
                            "get_categories" -> "正在获取分类..."
                            "get_past_task_performance" -> "正在分析历史执行效率..."
                            else -> "正在调用 $name..."
                        }
                        onProgress(progressMsg)

                        async {
                            val result = handleToolCall(name, arguments)
                            val resultSummary = when (name) {
                                "get_user_profile" -> "已获取用户画像数据"
                                "get_incomplete_tasks" -> {
                                    val count = result.lines().first()
                                        .replace("找到", "").substringBefore("条").toIntOrNull()
                                    if (count != null) "获取到${count}条未完成任务" else "已获取任务列表"
                                }
                                "get_task_details" -> "已查阅任务详情"
                                "search_completed_similar_tasks" -> "已检索历史相似任务"
                                "get_user_location" -> "已获取当前位置"
                                "calculate_route" -> "路线耗时计算完成"
                                "get_weather" -> "天气数据获取完成"
                                "search_nearby_location" -> "地点搜索完成"
                                "get_categories" -> "已获取任务分类"
                                "get_past_task_performance" -> "历史执行效率分析完成"
                                else -> "已调用 $name"
                            }
                            onProgress(resultSummary)
                            Triple(callId, result, resultSummary)
                        }
                    }
                    // 等待全部完成，收集结果
                    jobs.forEach { deferred ->
                        val (callId, result, _) = deferred.await()
                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", callId)
                            put("content", result)
                        })
                    }
                }
                retryCount++
            } else {
                val finalContent = message.getString("content")
                onProgress("正在整理建议...")
                return parseAdviceOutput(finalContent)
            }
        }
        return Result.failure(Exception("分析轮次过多"))
    }

    private fun parseAdviceOutput(content: String): Result<DailyScheduleAdviceEntity> {
        return try {
            val jsonStr = extractJson(content)
            val json = JSONObject(jsonStr)

            // 鲁棒性处理：兼容 JSONObject 或 JSONArray 格式
            val shortTerm = if (json.has("short_term")) {
                val st = json.get("short_term")
                when (st) {
                    is JSONArray -> st.toString()
                    is JSONObject -> JSONArray().apply { put(st) }.toString()
                    else -> "[]"
                }
            } else "[]"

            val longTerm = if (json.has("long_term")) {
                val lt = json.get("long_term")
                when (lt) {
                    is JSONArray -> lt.toString()
                    is JSONObject -> JSONArray().apply { put(lt) }.toString()
                    else -> "[]"
                }
            } else "[]"

            Result.success(
                DailyScheduleAdviceEntity(
                    shortTermJson = shortTerm,
                    longTermJson = longTerm,
                    generationStatus = "COMPLETED"
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("分析结果解析失败: ${e.message}"))
        }
    }

    suspend fun generateDirectAdvice(
        onProgress: (String) -> Unit
    ): Result<DailyScheduleAdviceEntity> {
        val apiKey = preferenceManager.getApiKey()
        if (apiKey.isNullOrBlank()) return Result.failure(Exception("未配置API Key"))

        onProgress("正在整理数据...")
        val profile = userProfileDao.getLatestUserProfile()
        val now = System.currentTimeMillis()
        val tasks = taskDao.getIncompleteTasksInRange(now, now + 5 * 86400000L).take(30)
        
        val profileText = if (profile != null) {
            "用户画像: 完成率${(profile.completionRate * 100).toInt()}%, 拖延率${(profile.delayedRate * 100).toInt()}%, " +
            "身份:${profile.identity}, 偏好:${profile.travelPreference}, 特征:${profile.personality}"
        } else "暂无画像"

        val tasksText = tasks.joinToString("\n") { t ->
            "- [${t.id}] ${t.title} 截止:${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t.deadline))}"
        }

        val prompt = """
            $profileText
            任务清单:
            $tasksText
            
            请作为专业的日程顾问，生成日程建议。
            要求：
            1. 严禁出现任何 Emoji 表情。
            2. 建议必须直接引用具体的任务标题，不要使用ID。
            3. 已完成任务仅作历史参考，不应出现在执行建议中；应聚焦于未完成与未来任务。
            4. 直接返回严格格式的纯JSON数据（不要被Markdown代码块包裹）。
            
            输出结构：
            {
              "short_term": [
                {
                  "task_title": "任务标题或统筹名称",
                  "advice_type": "建议类型(如: 执行建议、出行规划、统筹安排)",
                  "content": "具体的建议内容，描述要清晰且专业",
                  "info": "补充信息(如: 预计耗时30分钟)"
                }
              ],
              "long_term": [
                {
                  "topic": "关注任务或领域",
                  "advice_type": "规划类型(如: 风险预警、习惯建议)",
                  "content": "建议内容",
                  "urgency": "轻重缓急(高、中、低)"
                }
              ]
            }
        """.trimIndent()

        onProgress("正在生成建议...")

        val provider = aiProviderFactory.getProvider(preferenceManager.getAiProvider())
            ?: return Result.failure(Exception("当前 AI 提供商不支持日程建议"))
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", "你是日程专家，直接返回特定格式JSON。") })
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
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("AI 响应为空"))
            parseAdviceOutput(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJson(content: String): String {
        val start = content.indexOf("{")
        val end = content.lastIndexOf("}")
        if (start == -1 || end == -1 || end <= start) throw Exception("未找到JSON对象")
        return content.substring(start, end + 1)
    }
}
