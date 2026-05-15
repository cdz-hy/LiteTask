package com.litetask.app.data.ai

import com.litetask.app.data.model.Category
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskType
import com.litetask.app.data.repository.TaskRepositoryImpl
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIAgentAssistant @Inject constructor(
    private val taskRepository: TaskRepositoryImpl,
    private val categoryRepository: com.litetask.app.data.repository.CategoryRepository,
    private val locationProvider: com.litetask.app.util.LocationProvider,
    private val aMapRepository: com.litetask.app.data.repository.AMapRepository,
    private val preferenceManager: com.litetask.app.data.local.PreferenceManager
) {
    fun getToolsSchema(): JSONArray {
        val tools = JSONArray()

        // 1. get_recent_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_recent_tasks")
                put("description", "[必读]获取最近的任务列表。仅当用户提及‘最近’、‘刚才’或未指明具体任务但意图涉及现有日程时使用。你可以通过 limit 决定查几条(最大50)。如果没有找到想要的，请扩大 limit 再次(多次)调用或改用 search_tasks 工具。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("status", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("completed", "incomplete", "expired")))
                            put("description", "限选：completed, incomplete, expired。默认查询待办事项。")
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("description", "需要查询返回的条数，范围 1 到 50。如果没有找到相关的，你可以重新调用此参数。")
                        })
                    })
                })
            })
        })

        // 2. search_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_tasks")
                put("description", "[关键]精准检索任务简报。当你判定用户意图涉及特定任务时使用。仅返回 ID、标题等核心信息以节省 token。若需完整详情请调用 get_task_details。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "关键词，如‘会议’、‘报告’等")
                        })
                    })
                    put("required", JSONArray(listOf("keyword")))
                })
            })
        })

        // 3. get_task_details
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_task_details")
                put("description", "获取单个任务的完整详细信息（包括完整描述等）。仅在 search_tasks 或 get_recent_tasks 获得的简报不足以支持决策时调用。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("taskId", JSONObject().apply {
                            put("type", "integer")
                            put("description", "任务的唯一 ID")
                        })
                    })
                    put("required", JSONArray(listOf("taskId")))
                })
            })
        })

        // 4. get_categories
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_categories")
                put("description", "获取分类体系。当用户提到的任务类型不在常见认知范围内，或你想确保分类 ID 100% 正确时调用。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 4. get_user_location
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_user_location")
                put("description", "[谨慎使用]获取设备当前经纬度坐标。用于所有位置相关的辅助计算。输出格式: 'lng,lat'。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 6. search_nearby_location
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_nearby_location")
                put("description", "[地点模糊时使用，谨慎使用]根据关键词在周边搜索真实地址。如果用户提及多个地点，必须多次(多轮)或并发调用此工具分别查询。由于返回多个候选，请自行分析最符合意图的地点并采纳。若均不合适请换词重搜。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "用户想要寻找的地标或店铺名，如‘菜鸟驿站’、‘超市’")
                        })
                        put("radius", JSONObject().apply {
                            put("type", "integer")
                            put("description", "搜索半径(米)，如果用户要找的地方在跨市/远距离，可适当配置到最大 50000")
                        })
                    })
                    put("required", JSONArray(listOf("keyword")))
                })
            })
        })

        return tools
    }

    suspend fun handleToolCall(name: String, args: JSONObject): String {
        return when (name) {
            "get_recent_tasks" -> {
                val status = args.optString("status")
                val requestedLimit = args.optInt("limit", 10)
                val tasks = taskRepository.getRecentTasksWithLimit(status, requestedLimit)
                
                if (tasks.isEmpty()) {
                    return "当前分类下未找到任何任务。如果确信有任务存在，请扩大 limit 参数数量再次尝试，或考虑更换 status 状态。"
                }
                
                val array = JSONArray()
                tasks.forEach { composite ->
                    array.put(JSONObject().apply {
                        put("id", composite.task.id)
                        put("title", composite.task.title)
                        put("category", composite.category?.name ?: "默认")
                        put("deadline", SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(composite.task.deadline)))
                        // 移除 description 和 is_done 以节省 token
                    })
                }
                
                if (tasks.size < requestedLimit) {
                    "成功查找到 ${tasks.size} 条数据（已返回该分类下所有历史数据，无更多数据可查）:\n" + array.toString()
                } else {
                    "成功查找到最近的 ${tasks.size} 条数据。如果还需要看更早的数据，请在下一次调用时扩大 limit (\n" + array.toString() + ")"
                }
            }
            "search_tasks" -> {
                val keyword = args.optString("keyword")
                val tasks = taskRepository.searchTasksSync(keyword)
                val array = JSONArray()
                tasks.forEach { composite ->
                    array.put(JSONObject().apply {
                        put("id", composite.task.id)
                        put("title", composite.task.title)
                        put("category", composite.category?.name ?: "默认")
                        put("deadline", SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(composite.task.deadline)))
                    })
                }
                array.toString()
            }
            "get_task_details" -> {
                val taskId = args.optLong("taskId")
                val tasks = taskRepository.getTaskByIdSync(taskId)
                if (tasks != null) {
                    JSONObject().apply {
                        put("id", tasks.task.id)
                        put("title", tasks.task.title)
                        put("description", tasks.task.description) // 只有这里返回完整描述
                        put("category", tasks.category?.name ?: "默认")
                        put("startTime", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tasks.task.startTime)))
                        put("endTime", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tasks.task.deadline)))
                        put("is_done", tasks.task.isDone)
                    }.toString()
                } else {
                    "任务 ID 为 $taskId 的任务不存在。"
                }
            }
            "get_categories" -> {
                val categories = categoryRepository.getAllCategoriesSync()
                categories.joinToString(", ") { "${it.name} (ID: ${it.id})" }
            }
            "get_user_location" -> {
                val location = locationProvider.getCurrentLocation()
                if (location != null) {
                    "${location.longitude},${location.latitude}"
                } else {
                    if (!locationProvider.hasLocationPermission()) {
                        "无法获取位置：缺少定位权限。请在应用权限设置中开启定位权限。"
                    } else {
                        "无法获取位置：定位服务可能未开启或信号较弱。请检查设备定位设置。"
                    }
                }
            }
            "search_nearby_location" -> {
                val keyword = args.optString("keyword")
                val radiusMap = args.optInt("radius", 50000)
                
                // 检查高德地图API Key
                val amapKey = preferenceManager.getAMapKey()
                if (amapKey.isNullOrBlank()) {
                    return "无法进行周边搜索：未配置高德地图API Key。请在设置中配置地图服务。"
                }
                
                val location = locationProvider.getCurrentLocation()
                if (location != null) {
                    val locStr = "${location.longitude},${location.latitude}"
                    val results = aMapRepository.searchNearby(keyword, locStr, radiusMap)
                    if (results.isNotEmpty()) {
                        val limit = minOf(3, results.size)
                        val sb = StringBuilder("已找到以下候选地点，请自行分析最合适的一个(考虑类型匹配和距离)：\n")
                        for (i in 0 until limit) {
                            val r = results[i]
                            val distInfo = if (r.distance != null) "距离: ${r.distance}米" else ""
                            val typeInfo = if (!r.type.isNullOrBlank()) "类型: ${r.type}" else ""
                            sb.append("- 候选${i+1}: ${r.name}，${r.address}。$typeInfo $distInfo (坐标: ${r.lng},${r.lat})\n")
                        }
                        sb.toString()
                    } else {
                        "在附近 $radiusMap 米内未找到关于 '$keyword' 的地点。如果该地点可能在更远的地方，请尝试增大 radius 再次搜索（最大50000）；如果这确实是一个生僻或未收录的地点，请停止搜索，直接将该地名的原话作为目的地填入返回值即可。"
                    }
                } else {
                    if (!locationProvider.hasLocationPermission()) {
                        "获取不到当前位置：缺少定位权限。请在应用权限设置中开启定位权限后重试。"
                    } else {
                        "获取不到当前位置：定位服务可能未开启或信号较弱。建议用户手动输入详细地址。"
                    }
                }
            }
            else -> "未知工具"
        }
    }

    fun parseAgentOutput(content: String, originalText: String, categories: List<Category>): List<Task> {
        val tasks = mutableListOf<Task>()
        try {
            // 尝试提取 JSON 数组部分
            val startIndex = content.indexOf("[")
            val endIndex = content.lastIndexOf("]")
            if (startIndex == -1 || endIndex == -1 || endIndex < startIndex) {
                return emptyList()
            }
            val jsonPart = content.substring(startIndex, endIndex + 1)
            
            val array = JSONArray(jsonPart)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optLong("id", 0L)
                    val title = obj.getString("title")
                    val startStr = obj.optString("startTime", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
                    val endStr = obj.optString("endTime", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + 86400000)))
                    val categoryName = obj.optString("type", "工作")
                    val description = obj.optString("description", "")
                    val destination = obj.optString("destination", "")

                    val matchedCategory = categories.find { it.name.equals(categoryName, ignoreCase = true) }
                        ?: categories.firstOrNull { it.isDefault }
                    
                    val task = Task(
                        id = id,
                        title = title,
                        description = description,
                        startTime = dateFormat.parse(startStr)?.time ?: System.currentTimeMillis(),
                        deadline = dateFormat.parse(endStr)?.time ?: (System.currentTimeMillis() + 86400000),
                        categoryId = matchedCategory?.id ?: 1L,
                        originalVoiceText = originalText
                    )
                    task.parsedDestination = destination
                    tasks.add(task)
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tasks
    }
}
