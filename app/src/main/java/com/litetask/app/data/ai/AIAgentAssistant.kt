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
    private val aMapRepository: com.litetask.app.data.repository.AMapRepository
) {
    fun getToolsSchema(): JSONArray {
        val tools = JSONArray()

        // 1. get_recent_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_recent_tasks")
                put("description", "[必读]获取最近的任务列表。仅当用户提及‘最近’、‘刚才’或未指明具体任务但意图涉及现有日程时使用。严禁在处理明确的新增指令时盲目调用。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("status", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("completed", "incomplete", "expired")))
                            put("description", "限选：completed, incomplete, expired。默认查询待办事项。")
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
                put("description", "[关键]精准检索任务。当你判定用户意图是‘修改’、‘延期’、‘重命名’或‘标记完成’某项特定任务时，必须调用此工具定位 ID。调用前需从用户话语中提取核心关键词。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "从用户输入中提取的实体词，如‘会议’、‘报告’等")
                        })
                    })
                    put("required", JSONArray(listOf("keyword")))
                })
            })
        })

        // 3. get_categories
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
                put("description", "获取设备当前经纬度坐标。用于所有位置相关的辅助计算。输出格式: 'lng,lat'。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
            })
        })

        // 5. search_nearby_location
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_nearby_location")
                put("description", "[地点模糊时必用]根据关键词（如“菜鸟驿站”）在用户周边搜索最近的真实地址。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "用户想要寻找的地标或店铺名，如‘菜鸟驿站’、‘超市’")
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
                val tasks = taskRepository.getRecentTasksWithLimit(status)
                val array = JSONArray()
                tasks.forEach { composite ->
                    array.put(JSONObject().apply {
                        put("id", composite.task.id)
                        put("title", composite.task.title)
                        put("category", composite.category?.name ?: "默认")
                        put("deadline", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(composite.task.deadline)))
                        put("is_done", composite.task.isDone)
                        put("description", if (composite.task.description?.length ?: 0 > 20) composite.task.description?.substring(0, 20) + "..." else composite.task.description)
                    })
                }
                array.toString()
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
                        put("deadline", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(composite.task.deadline)))
                        put("description", if (composite.task.description?.length ?: 0 > 20) composite.task.description?.substring(0, 20) + "..." else composite.task.description)
                    })
                }
                array.toString()
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
                    "无法获取位置权限或GPS信号弱"
                }
            }
            "search_nearby_location" -> {
                val keyword = args.optString("keyword")
                val location = locationProvider.getCurrentLocation()
                if (location != null) {
                    val locStr = "${location.longitude},${location.latitude}"
                    val results = aMapRepository.searchNearby(keyword, locStr)
                    if (results.isNotEmpty()) {
                        val first = results[0]
                        "已为您找到最近的 ${first.endName}，位于：${first.endAddress}。坐标：${first.endLng},${first.endLat}"
                    } else {
                        "在您附近 5000 米内未找到相关地点"
                    }
                } else {
                    "获取不到当前位置，无法进行周边搜索。建议用户手动输入详细地址。"
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
