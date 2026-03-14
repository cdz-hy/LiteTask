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
    private val categoryRepository: com.litetask.app.data.repository.CategoryRepository
) {
    fun getToolsSchema(): JSONArray {
        val tools = JSONArray()

        // 1. get_recent_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_recent_tasks")
                put("description", "【必读】获取最近的任务列表。仅当用户提及‘最近’、‘刚才’或未指明具体任务但意图涉及现有日程时使用。严禁在处理明确的新增指令时盲目调用。")
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
                put("description", "【关键】精准检索任务。当你判定用户意图是‘修改’、‘延期’、‘重命名’或‘标记完成’某项特定任务时，必须调用此工具定位 ID。调用前需从用户话语中提取核心关键词。")
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
                put("description", "获取当前方位。仅当用户提到了类似‘去...’、‘到...附件’或需要规划路线相关建议，且原始输入未说明当前出发地时调用。辅助计算目的地偏移。")
                put("parameters", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
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
                "当前位置：上海市虹桥区域（模拟数据）"
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
