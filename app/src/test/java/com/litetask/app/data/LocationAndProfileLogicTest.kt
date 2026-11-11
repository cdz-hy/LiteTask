package com.litetask.app.data

import com.google.gson.Gson
import com.litetask.app.data.ai.DailyScheduleAdviceAssistant
import com.litetask.app.data.ai.ScheduleAdvicePromptSupport
import com.litetask.app.data.ai.ScheduleContextBuilder
import com.litetask.app.data.ai.UserProfileAnalyzer
import com.litetask.app.data.model.AIHistory
import com.litetask.app.data.model.AIHistorySource
import com.litetask.app.data.model.AMapRouteData
import com.litetask.app.data.model.BackupData
import com.litetask.app.data.model.Category
import com.litetask.app.data.model.DailyScheduleAdviceEntity
import com.litetask.app.data.model.FocusSessionEntity
import com.litetask.app.data.model.Reminder
import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.data.model.toEntity
import com.litetask.app.data.repository.AMapWeatherForecastDay
import com.litetask.app.data.repository.AMapWeatherReport
import com.litetask.app.util.ExternalCalendarEvent
import com.litetask.app.data.repository.AMapRepository
import com.litetask.app.data.repository.BackupRepository
import com.litetask.app.data.repository.LocationTracker
import com.litetask.app.ui.history.AIHistoryFilter
import com.litetask.app.ui.history.AIHistoryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAndProfileLogicTest {

    @Test
    fun `findMatchingLocation prefers 300m cluster first and blocks cross-city same-name collapse`() {
        val existing = listOf(
            UserLocationEntity(
                id = 1L,
                name = "中关村软件园一期",
                latitude = 40.0490,
                longitude = 116.2980,
                originCount = 5,
                destinationCount = 1
            ),
            UserLocationEntity(
                id = 2L,
                name = "西二旗地铁站",
                latitude = 40.0530,
                longitude = 116.3060,
                originCount = 0,
                destinationCount = 3
            ),
            UserLocationEntity(
                id = 3L,
                name = "万达广场",
                latitude = 39.9090,
                longitude = 116.4710,
                originCount = 0,
                destinationCount = 2
            )
        )

        // 1. 距离“中关村软件园一期”约 110 米，即使名字完全不同（甚至与远处“西二旗地铁站”同名），优先按 300m 物理聚类命中 1L
        val clusterOverName = LocationTracker.findMatchingLocation(
            name = "西二旗地铁站",
            lng = 116.2985,
            lat = 40.0499,
            existingLocations = existing
        )
        assertNotNull(clusterOverName)
        assertEquals(1L, clusterOverName?.id)

        // 2. 跨城市同名地点（上海五角场万达 vs 北京CBD万达，相距 > 1000km）禁止误合并
        val crossCitySameName = LocationTracker.findMatchingLocation(
            name = "万达广场",
            lng = 121.5150,
            lat = 31.3010,
            existingLocations = existing
        )
        assertNull(crossCitySameName)

        // 3. 无坐标兜底时仍支持精确同名匹配
        val noCoordSameName = LocationTracker.findMatchingLocation(
            name = "万达广场",
            lng = 0.0,
            lat = 0.0,
            existingLocations = existing
        )
        assertEquals(3L, noCoordSameName?.id)
    }

    @Test
    fun `extractBestResultFromRegeoJson captures representative name and formatted address`() {
        val regeoJson = """
            {
              "status": "1",
              "regeocode": {
                "formatted_address": "北京市海淀区上地十街10号",
                "addressComponent": {
                  "district": "海淀区",
                  "township": "马连洼街道",
                  "neighborhood": {"name": "中关村软件园"}
                },
                "aois": [
                  {"name": "百度科技园", "distance": "45.0"}
                ],
                "pois": [
                  {"name": "百度大厦1号门", "type": "门牌信息", "distance": "15.0"}
                ]
              }
            }
        """.trimIndent()
        val parsed = LocationTracker.extractBestResultFromRegeoJson(regeoJson)
        assertEquals("百度科技园", parsed.name)
        assertEquals("北京市海淀区上地十街10号", parsed.address)
    }

    @Test
    fun `pickBestLocationName prefers representative AOI or campus over doorway POI`() {
        val candidates = listOf(
            LocationTracker.LocationCandidate("3号楼东门", LocationTracker.CandidateKind.POI, "商务住宅", 20.0),
            LocationTracker.LocationCandidate("清华大学", LocationTracker.CandidateKind.AOI, "", 80.0),
            LocationTracker.LocationCandidate("海淀区", LocationTracker.CandidateKind.DISTRICT)
        )
        val picked = LocationTracker.pickBestLocationName(candidates)
        assertEquals("清华大学", picked)
    }

    @Test
    fun `computeObjectiveStats uses 30d delayedRate window and handles empty AI history`() {
        val now = 1_700_000_000_000L
        val categories = listOf(
            Category(id = 1L, name = "工作", colorHex = "#4285F4", isDefault = true),
            Category(id = 2L, name = "学习", colorHex = "#34A853")
        )
        // 旧任务中有一条 60 天前的逾期记录，但近 30 天均按时/正常进行中，近 30 天拖延率应为 0%
        val oldTime = now - 60L * 24 * 60 * 60 * 1000
        val recentTime = now - 2L * 24 * 60 * 60 * 1000
        val tasks = listOf(
            Task(id = 1, title = "旧工作1", deadline = oldTime + 3600_000L, isDone = true, completedAt = oldTime + 7200_000L, createdAt = oldTime, categoryId = 1L),
            Task(id = 2, title = "旧工作2", deadline = 0L, isDone = false, createdAt = oldTime, categoryId = 1L),
            Task(id = 3, title = "近期复习", deadline = now + 86_400_000L, isDone = false, createdAt = recentTime, categoryId = 2L),
            Task(id = 4, title = "近期刷题", deadline = now + 86_400_000L, isDone = false, createdAt = recentTime, categoryId = 2L)
        )

        val stats = UserProfileAnalyzer.computeObjectiveStats(
            allTasks = tasks,
            categories = categories,
            allAiHistories = emptyList<AIHistory>(),
            now = now
        )
        assertEquals(0.25, stats.completionRate, 1e-6)
        assertEquals(0.0, stats.delayedRate, 1e-6)
        assertEquals("学习", stats.categoryFocus)
        assertEquals("无 AI 记录（手动为主）", stats.inputPreference)

        // 若近 30 天内包含一条逾期任务（2条中有1条逾期），近30天拖延率应为 0.5
        val tasksWithRecentDelay = tasks.dropLast(1) + Task(
            id = 4,
            title = "近期逾期",
            deadline = now - 3600_000L,
            isDone = false,
            isExpired = true,
            createdAt = recentTime,
            categoryId = 2L
        )
        val delayedStats = UserProfileAnalyzer.computeObjectiveStats(
            allTasks = tasksWithRecentDelay,
            categories = categories,
            allAiHistories = listOf(
                AIHistory(content = "语音测试", sourceType = AIHistorySource.VOICE, parsedCount = 1, isSuccess = true)
            ),
            now = now
        )
        assertEquals(0.5, delayedStats.delayedRate, 1e-6)
        assertEquals("语音", delayedStats.inputPreference)
    }

    @Test
    fun `matchTravelMode maps free-form Chinese travel descriptions accurately`() {
        assertEquals("transit", DailyScheduleAdviceAssistant.matchTravelMode("地铁通勤"))
        assertEquals("transit", DailyScheduleAdviceAssistant.matchTravelMode("公交为主"))
        assertEquals("bicycling", DailyScheduleAdviceAssistant.matchTravelMode("共享单车骑行"))
        assertEquals("bicycling", DailyScheduleAdviceAssistant.matchTravelMode("电动车短途"))
        assertEquals("walking", DailyScheduleAdviceAssistant.matchTravelMode("步行优先"))
        assertEquals("driving", DailyScheduleAdviceAssistant.matchTravelMode("自驾开车"))
        assertEquals("driving", DailyScheduleAdviceAssistant.matchTravelMode(null))
    }

    @Test
    fun `buildRouteUrl uses v4 for bicycling and includes city for transit`() {
        val bikeUrl = AMapRepository.buildRouteUrl(116.3, 39.9, 116.4, 39.95, "bicycling", "k1")!!
        assertTrue(bikeUrl.startsWith("https://restapi.amap.com/v4/direction/bicycling"))

        val transitUrl = AMapRepository.buildRouteUrl(121.4, 31.2, 121.5, 31.25, "transit", "k1", city = "上海市")!!
        assertTrue(transitUrl.contains("/v3/direction/transit/integrated"))
        assertTrue(transitUrl.contains("city="))
    }

    @Test
    fun `formatAiHistoryForPrompt distinguishes sources, images, targets and avoids noise from failures`() {
        assertEquals("无AI交互记录。", UserProfileAnalyzer.formatAiHistoryForPrompt(emptyList(), 10))

        val mixed = listOf(
            AIHistory(id = 1, content = "网络失败样本", sourceType = AIHistorySource.TEXT, parsedCount = 0, isSuccess = false, errorMessage = "超时", timestamp = 4000L),
            AIHistory(id = 2, content = "", sourceType = AIHistorySource.TEXT, parsedCount = 2, isSuccess = true, imageCount = 2, timestamp = 3000L),
            AIHistory(id = 3, content = "", sourceType = AIHistorySource.SUBTASK, parsedCount = 4, isSuccess = true, targetTitle = "准备期末答辩", timestamp = 2000L),
            AIHistory(id = 4, content = "明天上午十点去医院复查", sourceType = AIHistorySource.VOICE, parsedCount = 1, isSuccess = true, timestamp = 1000L)
        )
        val formattedMixed = UserProfileAnalyzer.formatAiHistoryForPrompt(mixed, 10)
        assertFalse(formattedMixed.contains("网络失败样本"))
        assertTrue(formattedMixed.contains("- [文字] [纯图片识别(2张)]"))
        assertTrue(formattedMixed.contains("- [子任务拆解] 目标「准备期末答辩」 [一键自动拆解]"))
        assertTrue(formattedMixed.contains("- [语音] 明天上午十点去医院复查"))

        val onlyFailed = listOf(
            AIHistory(id = 5, content = "请整理截图待办", sourceType = AIHistorySource.TEXT, parsedCount = 0, isSuccess = false, imageCount = 1, timestamp = 5000L)
        )
        val formattedFailed = UserProfileAnalyzer.formatAiHistoryForPrompt(onlyFailed, 10)
        assertTrue(formattedFailed.contains("- [文字] 请整理截图待办 (含1张图) [未成功]"))
    }

    @Test
    fun `filterAiHistory filters by source, failure status and searches content targetTitle and error`() {
        val items = listOf(
            AIHistory(id = 1, content = "周报梳理", sourceType = AIHistorySource.TEXT, parsedCount = 2, isSuccess = true),
            AIHistory(id = 2, content = "买牛奶", sourceType = AIHistorySource.VOICE, parsedCount = 0, isSuccess = false, errorMessage = "网络连接失败"),
            AIHistory(id = 3, content = "重点核对实验数据", sourceType = AIHistorySource.SUBTASK, parsedCount = 3, isSuccess = true, targetTitle = "完成毕业论文")
        )

        assertEquals(3, AIHistoryViewModel.filterAiHistory(items, AIHistoryFilter.ALL, "").size)
        assertEquals(listOf(2L), AIHistoryViewModel.filterAiHistory(items, AIHistoryFilter.FAILED, "").map { it.id })
        assertEquals(listOf(3L), AIHistoryViewModel.filterAiHistory(items, AIHistoryFilter.SUBTASK, "毕业论文").map { it.id })
        assertEquals(listOf(2L), AIHistoryViewModel.filterAiHistory(items, AIHistoryFilter.ALL, "网络").map { it.id })
    }

    @Test
    fun `backup sanitization handles missing Gson fields and deduplicates AIHistory and Advice`() {
        // 模拟缺省 content / source_type / image_count 的精简 JSON 导入
        val legacyLikeJson = """
            {
              "ai_history": [
                {"id": 9, "timestamp": 1700000123456, "parsed_count": -2, "is_success": true, "target_title": "  复习高数  "}
              ],
              "daily_schedule_advice": [
                {"id": 4, "created_at": 1700000200000, "generation_status": "GENERATING"},
                {"id": 5, "created_at": 1700000300000, "short_term_json": "[{\"task_title\":\"写作业\"}]", "long_term_json": "", "generation_status": "COMPLETED"}
              ]
            }
        """.trimIndent()
        val parsed = Gson().fromJson(legacyLikeJson, BackupData::class.java)
        val rawHistory = parsed.aiHistory!!.single()
        val sanitizedHistory = BackupRepository.sanitizeImportedAiHistory(rawHistory)

        assertEquals(0L, sanitizedHistory.id)
        assertEquals("", sanitizedHistory.content)
        assertEquals(AIHistorySource.TEXT, sanitizedHistory.sourceType)
        assertEquals(0, sanitizedHistory.parsedCount)
        assertEquals(0, sanitizedHistory.imageCount)
        assertEquals("复习高数", sanitizedHistory.targetTitle)

        // 不同 targetTitle 但同为 empty content 的两条拆解记录不应碰撞指纹
        val anotherSubtask = sanitizedHistory.copy(sourceType = AIHistorySource.SUBTASK, targetTitle = "准备汇报")
        assertTrue(
            BackupRepository.aiHistoryFingerprint(sanitizedHistory) !=
                BackupRepository.aiHistoryFingerprint(anotherSubtask)
        )

        val rawAdvices = parsed.dailyScheduleAdvice!!
        assertNull(BackupRepository.sanitizeImportedDailyAdvice(rawAdvices[0]))
        val validAdvice = BackupRepository.sanitizeImportedDailyAdvice(rawAdvices[1])
        assertNotNull(validAdvice)
        assertEquals(0L, validAdvice!!.id)
        assertEquals("[]", validAdvice.longTermJson)
    }

    @Test
    fun `schedule prompt support builds composite summaries, conditional prompts and robust JSON`() {
        val now = 1_760_000_000_000L
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val workCat = Category(id = 2L, name = "工作", colorHex = "#4285F4")
        val routeEntity = TaskComponent.AMapComponent(
            id = 1L,
            taskId = 10L,
            data = AMapRouteData(endName = "朝阳医院", endAddress = "工体南路8号", endLat = 39.92, endLng = 116.44)
        ).toEntity(10L)

        val upcomingComposite = TaskDetailComposite(
            task = Task(
                id = 10L,
                title = "下午体检复查",
                description = "携带既往病历本和医保卡",
                startTime = now + 3_600_000L,
                deadline = now + 7_200_000L,
                isPinned = true,
                totalFocusSeconds = 1500L,
                categoryId = 2L
            ),
            subTasks = listOf(
                SubTask(id = 1L, taskId = 10L, content = "打印预约单", isCompleted = true),
                SubTask(id = 2L, taskId = 10L, content = "空腹准备", isCompleted = false)
            ),
            reminders = listOf(Reminder(id = 1L, taskId = 10L, triggerAt = now + 1_800_000L)),
            category = workCat,
            components = listOf(routeEntity)
        )
        val overdueComposite = TaskDetailComposite(
            task = Task(
                id = 11L,
                title = "提交项目周报",
                startTime = now - 10_800_000L,
                deadline = now - 7_200_000L,
                isDone = false
            ),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null
        )

        val detailJson = ScheduleAdvicePromptSupport.toTaskJson(upcomingComposite, now, includeDetails = true, zoneId = zone)
        assertEquals("工作", detailJson.get("category").asString)
        assertEquals("1/2", detailJson.get("subtask_progress").asString)
        assertTrue(detailJson.get("has_route").asBoolean)
        assertEquals("朝阳医院", detailJson.get("route_destination").asString)
        assertEquals(25L, detailJson.get("focused_minutes").asLong)
        assertEquals(1, detailJson.get("reminders").asJsonArray.size())
        assertEquals(1, detailJson.get("components").asJsonArray.size())

        val formattedTasks = ScheduleAdvicePromptSupport.formatTasksForDirectPrompt(
            upcoming = listOf(upcomingComposite),
            overdue = listOf(overdueComposite),
            nowMillis = now,
            zoneId = zone
        )
        assertTrue(formattedTasks.contains("【已逾期未完成任务（共1条）】"))
        assertTrue(formattedTasks.contains("已逾期2小时"))
        assertTrue(formattedTasks.contains("【未来未完成任务（共1条）】"))
        assertTrue(formattedTasks.contains("置顶"))
        assertTrue(formattedTasks.contains("路线目的地:朝阳医院"))

        val focusSummary = ScheduleAdvicePromptSupport.formatRecentFocusSummaryText(
            sessions = listOf(FocusSessionEntity(taskId = 10L, actualSeconds = 1800L, startedAt = now - 3600_000L)),
            completedRecent = listOf(upcomingComposite)
        )
        assertTrue(focusSummary.contains("近14天专注1次"))
        assertTrue(focusSummary.contains("「下午体检复查」"))

        val agentPromptNoMap = ScheduleAdvicePromptSupport.buildAgentSystemPrompt(
            currentDate = "2025-10-10 09:00 星期五",
            hasAmapKey = false,
            hasLocationPermission = false,
            toneHint = "温柔鼓励"
        )
        assertTrue(agentPromptNoMap.contains("get_overdue_tasks"))
        assertFalse(agentPromptNoMap.contains("get_categories"))
        assertFalse(agentPromptNoMap.contains("calculate_route"))
        assertTrue(agentPromptNoMap.contains("related_task_ids"))
        assertTrue(agentPromptNoMap.contains("suggested_focus_minutes"))
        assertTrue(agentPromptNoMap.contains("温柔鼓励"))

        val fenced = """
            ```json
            {
              "short_term": {"task_id": null, "related_task_ids": [10, 11], "task_title": "上午统筹"},
              "long_term": []
            }
            ```
        """.trimIndent()
        val parsed = ScheduleAdvicePromptSupport.parseAdviceJson(fenced).getOrThrow()
        assertTrue(parsed.first.startsWith("["))
        assertTrue(parsed.first.contains("上午统筹"))
        assertEquals("[]", parsed.second)

        assertTrue(ScheduleAdvicePromptSupport.parseAdviceJson("{\"short_term\":[],\"long_term\":[]}").isSuccess)
        assertTrue(ScheduleAdvicePromptSupport.parseAdviceJson("{\"other\": 1}").isFailure)
    }

    @Test
    fun `time slot calculator excludes all-day events and long DDL tasks while detecting overlaps and free slots`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val day = java.time.LocalDate.of(2025, 10, 15)
        fun atHour(dayOffset: Long, hour: Int, minute: Int = 0): Long =
            day.plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

        val now = atHour(0, 9, 0) // 09:00

        // 固定短任务: 10:00~12:00；长跨度 DDL 任务: 09:00 ~ 后天18:00 (>8h 不应阻塞空闲段)
        val shortTask = TaskDetailComposite(
            task = Task(id = 1L, title = "需求评审", startTime = atHour(0, 10, 0), deadline = atHour(0, 12, 0)),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null
        )
        val longDdlTask = TaskDetailComposite(
            task = Task(id = 2L, title = "季度汇报PPT", startTime = now, deadline = atHour(2, 18, 0)),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null
        )
        val mirroredCopyTask = TaskDetailComposite(
            task = Task(id = 3L, title = "团队站会", startTime = atHour(1, 9, 30), deadline = atHour(1, 10, 0)),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null
        )

        val externalEvents = listOf(
            // 24小时全天节假日事件 -> 应过滤掉
            ExternalCalendarEvent(100L, "全天纪念日", null, atHour(0, 0, 0), atHour(1, 0, 0), null, emptyList()),
            // 11:00~12:30 外部会议 -> 与 10:00~12:00 短任务重叠 60 分钟
            ExternalCalendarEvent(101L, "客户沟通会", null, atHour(0, 11, 0), atHour(0, 12, 30), "A座会议室", emptyList()),
            // 明日同名同时间镜像日程 -> 不应报伪冲突
            ExternalCalendarEvent(102L, "团队站会", null, atHour(1, 9, 30), atHour(1, 10, 0), null, emptyList())
        )

        val analysis = ScheduleContextBuilder.calculateTimeSlots(
            nowMillis = now,
            composites = listOf(shortTask, longDdlTask, mirroredCopyTask),
            externalEvents = externalEvents,
            zoneId = zone
        )

        assertEquals(1, analysis.longWindowTaskCount)
        assertEquals(1, analysis.conflicts.size)
        assertEquals(60L, analysis.conflicts.single().overlapMinutes)

        // 今日有效窗口 09:00~22:30；被占 10:00~12:30；空闲应为 09:00~10:00(60分) 与 12:30~22:30(600分)
        assertEquals(2, analysis.freeSlotsToday.size)
        assertEquals(60L, analysis.freeSlotsToday[0].durationMinutes)
        assertEquals(600L, analysis.freeSlotsToday[1].durationMinutes)

        // 明日有效窗口 08:00~22:30；被占 09:30~10:00；空闲应为 08:00~09:30(90分) 与 10:00~22:30(750分)
        assertEquals(2, analysis.freeSlotsTomorrow.size)
        assertEquals(90L, analysis.freeSlotsTomorrow[0].durationMinutes)
        assertEquals(750L, analysis.freeSlotsTomorrow[1].durationMinutes)

        val promptSummary = analysis.toPromptSummary(zone)
        assertTrue(promptSummary.contains("09:00-10:00(60分)"))
        assertTrue(promptSummary.contains("重叠60分钟"))
        assertTrue(promptSummary.contains("长跨度(>8小时)"))
    }

    @Test
    fun `focus energy analyzer detects golden bucket, formats weather and previous advice summaries`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val day = java.time.LocalDate.of(2025, 10, 15)
        fun atHour(hour: Int): Long = day.atTime(hour, 15).atZone(zone).toInstant().toEpochMilli()

        val emptyEnergy = ScheduleContextBuilder.analyzeFocusEnergyPattern(emptyList(), emptyList(), zone)
        assertNull(emptyEnergy.goldenWindowLabel)

        val sessions = listOf(
            FocusSessionEntity(id = 1L, actualSeconds = 1500L, startedAt = atHour(9)),  // 上午 25分
            FocusSessionEntity(id = 2L, actualSeconds = 2700L, startedAt = atHour(20)), // 晚间 45分
            FocusSessionEntity(id = 3L, actualSeconds = 3000L, startedAt = atHour(21))  // 晚间 50分
        )
        val completed = listOf(
            TaskDetailComposite(
                task = Task(id = 1L, title = "刷算法题", startTime = atHour(19), deadline = atHour(22), isDone = true, completedAt = atHour(21)),
                subTasks = emptyList(), reminders = emptyList(), category = null
            ),
            TaskDetailComposite(
                task = Task(id = 2L, title = "阅读论文", startTime = atHour(19), deadline = atHour(22), isDone = true, completedAt = atHour(22)),
                subTasks = emptyList(), reminders = emptyList(), category = null
            )
        )

        val energy = ScheduleContextBuilder.analyzeFocusEnergyPattern(sessions, completed, zone)
        assertEquals("晚间 18:00-23:00", energy.goldenWindowLabel)
        assertEquals(3, energy.totalSessions)
        assertEquals(120L, energy.totalFocusMinutes)
        assertEquals(40.0, energy.avgSessionMinutes, 1e-6)
        assertTrue(energy.toPromptSummary().contains("黄金高效时段: 晚间 18:00-23:00"))

        val weatherText = ScheduleContextBuilder.formatWeatherForecastText(
            AMapWeatherReport(
                city = "北京市",
                adcode = "110000",
                reportTime = "2025-10-15 08:00",
                forecasts = listOf(
                    AMapWeatherForecastDay("2025-10-15", "3", "晴", "多云", "20", "10", "北", "北")
                )
            )
        )
        assertTrue(weatherText.contains("北京市"))
        assertTrue(weatherText.contains("晴转多云 10~20°C"))

        val prevSummary = ScheduleContextBuilder.buildPreviousAdviceSummaryJson(
            listOf(
                DailyScheduleAdviceEntity(
                    createdAt = atHour(8),
                    shortTermJson = """[{"task_title":"刷算法题","action_type":"FOCUS"}]""",
                    longTermJson = """[{"topic":"晚间防疲劳"}]""",
                    generationStatus = "COMPLETED"
                )
            ),
            zone
        )
        assertTrue(prevSummary.contains("刷算法题[FOCUS]"))
        assertTrue(prevSummary.contains("晚间防疲劳"))

        val directPrompt = ScheduleAdvicePromptSupport.buildDirectUserPrompt(
            currentDate = "2025-10-15 09:00 星期三",
            profileText = "用户画像",
            tasksText = "任务列表",
            recentFocusText = "近期概览",
            toneHint = null,
            calendarContextText = "- 10-15 11:00~12:30 客户沟通会",
            freeSlotsText = "今日剩余可用时段: 09:00-10:00(60分)",
            focusEnergyText = energy.toPromptSummary(),
            weatherText = weatherText
        )
        assertTrue(directPrompt.contains("系统日历占用:"))
        assertTrue(directPrompt.contains("今明可用时间与冲突分析:"))
        assertTrue(directPrompt.contains("专注精力节律:"))
        assertTrue(directPrompt.contains("近期天气:"))
        assertTrue(com.litetask.app.util.CalendarSyncPrefsState().scheduleAdviceUseCalendar)
    }

    @Test
    fun `profile analyzer summarizes route travelMode short keys and ETA`() {
        val emptyMsg = com.litetask.app.data.ai.UserProfileAnalyzer.summarizeRoutePreferences(emptyList())
        assertTrue(emptyMsg.contains("暂未使用"))

        val shortKeyTransit = com.litetask.app.data.model.TaskComponentEntity(
            id = 1L,
            taskId = 10L,
            type = com.litetask.app.data.model.ComponentType.AMAP_ROUTE,
            dataJson = """{"b":"杭州东站","c":"天城路1号","d":30.29,"e":120.21,"g":2,"j":"transit","k":1800}"""
        )
        val drivingRoute = TaskComponent.AMapComponent(
            id = 2L,
            taskId = 11L,
            data = AMapRouteData(
                endName = "科技园",
                endAddress = "科苑路10号",
                endLat = 22.54,
                endLng = 113.95,
                strategy = 1,
                travelMode = "driving",
                estimatedDurationSec = 1200
            )
        ).toEntity(11L)

        val summary = com.litetask.app.data.ai.UserProfileAnalyzer.summarizeRoutePreferences(
            listOf(shortKeyTransit, drivingRoute)
        )
        assertTrue(summary.contains("公交地铁"))
        assertTrue(summary.contains("驾车"))
        assertTrue(summary.contains("少收费"))
        assertTrue(summary.contains("杭州东站"))
        assertTrue(summary.contains("25分钟"))
    }

    @Test
    fun `profile analyzer summarizes component habits across links passes attachments recurrence and voice`() {
        val now = 1_760_000_000_000L
        val composite = TaskDetailComposite(
            task = Task(id = 1L, title = "每周算法复盘", startTime = now, deadline = now + 3_600_000L),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null,
            components = listOf(
                TaskComponent.LinkComponent(
                    id = 1L,
                    taskId = 1L,
                    data = com.litetask.app.data.model.LinkBookmarkData(url = "https://github.com/org/repo", linkCategory = "CODE")
                ).toEntity(1L),
                TaskComponent.AccessPassComponent(
                    id = 2L,
                    taskId = 1L,
                    data = com.litetask.app.data.model.AccessPassData(passType = "MEETING", title = "周会", primaryCode = "123456")
                ).toEntity(1L),
                TaskComponent.FileComponent(
                    id = 3L,
                    taskId = 1L,
                    data = com.litetask.app.data.model.FileAttachmentData(fileName = "review.pdf", fileUri = "content://doc/1", mimeType = "application/pdf", fileSize = 1024L)
                ).toEntity(1L),
                TaskComponent.RecurrenceComponent(
                    id = 4L,
                    taskId = 1L,
                    data = com.litetask.app.data.model.RecurrenceRuleData(freq = "WEEKLY", interval = 1)
                ).toEntity(1L),
                TaskComponent.VoiceNoteComponent(
                    id = 5L,
                    taskId = 1L,
                    data = com.litetask.app.data.model.VoiceNoteData(filePath = "/tmp/a.m4a", durationMs = 5000L)
                ).toEntity(1L)
            )
        )

        val emptySummary = com.litetask.app.data.ai.UserProfileAnalyzer.summarizeComponentHabits(emptyList())
        assertTrue(emptySummary.contains("暂未使用"))

        val summary = com.litetask.app.data.ai.UserProfileAnalyzer.summarizeComponentHabits(listOf(composite))
        assertTrue(summary.contains("周期重复"))
        assertTrue(summary.contains("每周"))
        assertTrue(summary.contains("书签/链接"))
        assertTrue(summary.contains("GitHub"))
        assertTrue(summary.contains("凭证/号牌"))
        assertTrue(summary.contains("会议号"))
        assertTrue(summary.contains("文件附件"))
        assertTrue(summary.contains("PDF"))
        assertTrue(summary.contains("语音备忘"))
    }

    @Test
    fun `profile analyzer summarizes 30d rhythm focus energy and validates objective stats count`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val morningInstant = java.time.LocalDateTime.of(2026, 3, 18, 9, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val now = morningInstant + 2 * 24 * 3_600_000L
        val oldTime = now - 45L * 24 * 3_600_000L

        val tasks = listOf(
            Task(id = 1L, title = "近期上午任务1", createdAt = morningInstant, startTime = morningInstant, deadline = morningInstant + 3_600_000L, completedAt = morningInstant + 1_800_000L, isDone = true),
            Task(id = 2L, title = "近期上午任务2", createdAt = morningInstant + 1_000L, startTime = morningInstant + 1_000L, deadline = morningInstant + 7_200_000L, isDone = false),
            Task(id = 3L, title = "45天前旧任务", createdAt = oldTime, startTime = oldTime, deadline = oldTime + 3_600_000L, isDone = true)
        )
        val sessions = listOf(
            FocusSessionEntity(taskId = 1L, actualSeconds = 1800L, startedAt = morningInstant),
            FocusSessionEntity(taskId = 2L, actualSeconds = 20L, startedAt = morningInstant + 60_000L) // < 60s ignored
        )

        val stats = com.litetask.app.data.ai.UserProfileAnalyzer.computeObjectiveStats(
            allTasks = tasks,
            categories = emptyList(),
            allAiHistories = emptyList(),
            now = now
        )
        assertEquals(2, stats.recentTaskCount)

        val rhythm = com.litetask.app.data.ai.UserProfileAnalyzer.summarizeTimeAndFocusPattern(
            allTasks = tasks,
            focusSessions = sessions,
            now = now,
            zoneId = zone
        )
        assertTrue(rhythm.contains("近30天活跃/新建 2 条任务"))
        assertTrue(rhythm.contains("工作日"))
        assertTrue(rhythm.contains("有效专注(>=1分钟) 1 次"))
        assertTrue(rhythm.contains("黄金高效时段"))
    }

    @Test
    fun `profile analyzer parses fenced json sanitizes nulls and clamps scores`() {
        val raw = """
            分析如下：
            ```json
            {
              "identity": "软件工程师",
              "industry": "null",
              "travel_preference": "",
              "delay_index": 0,
              "plan_ability": 9,
              "stress_level": 4,
              "execution_rhythm": "上午高效推进",
              "ai_tone": "理性简洁，直接给行动建议",
              "personality": "目标导向且注重结构化拆解"
            }
            ```
        """.trimIndent()

        val parsed = com.litetask.app.data.ai.UserProfileAnalyzer.parseProfileJsonToEntity(
            analysisResult = raw,
            completionRate = 0.85,
            delayedRate = 0.12,
            inputPreference = "语音优先",
            categoryFocus = "工作",
            createdAt = 12345L
        ).getOrThrow()

        assertEquals("软件工程师", parsed.identity)
        assertEquals("未知领域", parsed.industry)
        assertEquals("暂无明显偏好", parsed.travelPreference)
        assertEquals(1, parsed.delayIndex)
        assertEquals(5, parsed.planAbility)
        assertEquals(4, parsed.stressLevel)
        assertEquals("上午高效推进", parsed.executionRhythm)
        assertEquals(12345L, parsed.createdAt)
        assertEquals(com.litetask.app.data.model.UserProfileHistoryEntity.SOURCE_AI, parsed.source)

        assertTrue(
            com.litetask.app.data.ai.UserProfileAnalyzer.parseProfileJsonToEntity(
                analysisResult = "没有返回JSON格式",
                completionRate = 0.0,
                delayedRate = 0.0,
                inputPreference = "平衡",
                categoryFocus = "暂无",
                createdAt = 12345L
            ).isFailure
        )
    }
}
