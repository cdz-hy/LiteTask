package com.litetask.app.data.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.litetask.app.data.model.AccessPassData
import com.litetask.app.data.model.LinkAndPassSupport
import com.litetask.app.data.model.LinkBookmarkData
import com.litetask.app.data.model.RecurrenceEngine
import com.litetask.app.data.model.RecurrenceRuleData
import com.litetask.app.data.model.Task
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统一解析 AI 输出 JSON 中附加组件草稿（链接、凭证号牌、周期重复等）并汇总共享 Prompt Schema。
 * 使用 Gson 解析，确保纯 JVM 单元测试与 Android 运行时行为完全一致。
 */
object AiTaskComponentParser {

    const val PROMPT_COMPONENT_RULES = """
- **网址与深链接 (`links`)**: 若文本中出现明确网页 URL（如文档、PR、会议链接）或 App 深链接，放入可选数组 `links`: `[{"url":"https://...","title":"简短标题","category":"WEB|CODE|DOC|VIDEO|SHOP|DEEPLINK"}]`。
- **凭证/号牌卡 (`passes`)**: 若包含会议号/入会密码、快递取件码、高铁车次座位/检票口、核销码/预约码/座位号，放入可选数组 `passes`: `[{"passType":"MEETING|PICKUP|TICKET|VERIFY|SEAT|OTHER","title":"如腾讯会议/菜鸟驿站","primaryCode":"核心号码","secondarySecret":"密码或检票口(可选)","providerName":"平台名(可选)","actionUrl":"可选直达链接"}]`。
- **周期重复规则 (`recurrence`)**: 若表达了规律循环（如"每天"、"每3天"、"工作日"、"每周三五"、"每月15号"、"每年"、"共5次"、"到月底截止"），放入可选对象 `recurrence`: `{"freq":"DAILY|WEEKLY|MONTHLY|YEARLY|INTERVAL_DAYS","interval":1,"daysOfWeek":[3,5],"dayOfMonth":15,"endType":"NEVER|AFTER_COUNT|UNTIL_DATE","remainingCount":5,"untilDate":"2026-12-31"}` (`daysOfWeek` 为 ISO 周一=1..周日=7)。"""

    const val PROMPT_JSON_FIELDS =
        """"links":[{"url":"https://...","title":"..."}],"passes":[{"passType":"MEETING","title":"...","primaryCode":"...","secondarySecret":"..."}],"recurrence":{"freq":"WEEKLY","interval":1,"daysOfWeek":[3,5],"endType":"NEVER"}"""

    fun applyParsedExtras(task: Task, rawJsonObject: String) {
        val obj = runCatching { JsonParser.parseString(rawJsonObject).asJsonObject }.getOrNull() ?: return

        val parsedLinks = mutableListOf<LinkBookmarkData>()
        val seenUrls = HashSet<String>()

        obj.getAsJsonArrayOrNull("links")?.forEach { elem ->
            val item = elem.asJsonObjectOrNull() ?: return@forEach
            val rawUrl = item.optStr("url") ?: return@forEach
            val normalizedUrl = LinkAndPassSupport.normalizeLaunchUrl(rawUrl)
            if (normalizedUrl.isBlank() || !seenUrls.add(normalizedUrl.lowercase())) return@forEach

            val rawTitle = item.optStr("title")
            val brand = LinkAndPassSupport.resolveBrandMeta(normalizedUrl, item.optStr("category") ?: item.optStr("linkCategory"))
            parsedLinks.add(
                LinkBookmarkData(
                    url = normalizedUrl,
                    title = rawTitle?.takeIf { it.isNotBlank() } ?: brand.brandName,
                    linkCategory = brand.category.code,
                    note = item.optStr("note")
                )
            )
        }
        task.parsedLinks = parsedLinks

        val parsedPasses = mutableListOf<AccessPassData>()
        val seenCodes = HashSet<String>()
        obj.getAsJsonArrayOrNull("passes")?.forEach { elem ->
            val item = elem.asJsonObjectOrNull() ?: return@forEach
            val primaryCode = (item.optStr("primaryCode") ?: item.optStr("primary_code") ?: item.optStr("code"))
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach
            if (!seenCodes.add(primaryCode.lowercase())) return@forEach

            val passType = LinkAndPassSupport.AccessPassType.fromCode(
                item.optStr("passType") ?: item.optStr("pass_type") ?: item.optStr("type")
            ).code
            val title = item.optStr("title")
                ?.takeIf { it.isNotBlank() }
                ?: LinkAndPassSupport.AccessPassType.fromCode(passType).label
            val secret = item.optStr("secondarySecret") ?: item.optStr("secondary_secret") ?: item.optStr("secret")
            val provider = item.optStr("providerName") ?: item.optStr("provider_name") ?: item.optStr("provider")
            val actionUrl = (item.optStr("actionUrl") ?: item.optStr("action_url"))
                ?.let { LinkAndPassSupport.normalizeLaunchUrl(it) }
                ?.takeIf { it.isNotBlank() }

            parsedPasses.add(
                AccessPassData(
                    passType = passType,
                    title = title,
                    primaryCode = primaryCode,
                    secondarySecret = secret,
                    providerName = provider,
                    actionUrl = actionUrl
                )
            )
        }
        task.parsedPasses = parsedPasses
        task.parsedRecurrence = parseRecurrenceObject(obj)
    }

    private fun parseRecurrenceObject(root: JsonObject): RecurrenceRuleData? {
        val rec = root.get("recurrence")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val freq = RecurrenceEngine.RecurrenceFreq.fromCode(
            rec.optStr("freq") ?: rec.optStr("frequency")
        ).code
        val interval = (rec.optInt("interval") ?: 1).coerceIn(1, 365)
        val daysOfWeek = (rec.getAsJsonArrayOrNull("daysOfWeek") ?: rec.getAsJsonArrayOrNull("days_of_week"))
            ?.mapNotNull { runCatching { it.asInt }.getOrNull() }
            ?.filter { it in 1..7 }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
        val dayOfMonth = (rec.optInt("dayOfMonth") ?: rec.optInt("day_of_month"))?.coerceIn(1, 31)

        val remainingCount = (rec.optInt("remainingCount")
            ?: rec.optInt("remaining_count")
            ?: rec.optInt("count"))?.takeIf { it > 0 }
        val untilEpochMs = rec.optLong("untilEpochMs")
            ?: rec.optLong("until_epoch_ms")
            ?: (rec.optStr("untilDate") ?: rec.optStr("until_date"))?.let(::parseDateEndMillis)

        val rawEndType = rec.optStr("endType") ?: rec.optStr("end_type")
        val endType = when {
            !rawEndType.isNullOrBlank() -> RecurrenceEngine.RecurrenceEndType.fromCode(rawEndType).code
            remainingCount != null -> RecurrenceEngine.RecurrenceEndType.AFTER_COUNT.code
            untilEpochMs != null -> RecurrenceEngine.RecurrenceEndType.UNTIL_DATE.code
            else -> RecurrenceEngine.RecurrenceEndType.NEVER.code
        }
        val inheritSubTasks = rec.optBool("inheritSubTasks")
            ?: rec.optBool("inherit_subtasks")
            ?: true

        return RecurrenceRuleData(
            freq = freq,
            interval = interval,
            daysOfWeek = daysOfWeek,
            dayOfMonth = dayOfMonth,
            endType = endType,
            remainingCount = remainingCount,
            untilEpochMs = untilEpochMs,
            inheritSubTasks = inheritSubTasks
        )
    }

    private fun parseDateEndMillis(rawDate: String): Long? = runCatching {
        LocalDate.parse(rawDate.take(10))
            .atTime(23, 59, 59)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun JsonObject.getAsJsonArrayOrNull(key: String) =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.optStr(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return runCatching { el.asString }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.optInt(key: String): Int? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return runCatching { el.asInt }.getOrNull()
    }

    private fun JsonObject.optLong(key: String): Long? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return runCatching { el.asLong }.getOrNull()
    }

    private fun JsonObject.optBool(key: String): Boolean? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return runCatching { el.asBoolean }.getOrNull()
    }
}
