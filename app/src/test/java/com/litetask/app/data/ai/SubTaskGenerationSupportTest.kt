package com.litetask.app.data.ai

import com.litetask.app.data.model.SubTask
import com.litetask.app.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubTaskGenerationSupportTest {

    private fun sampleTask(
        id: Long = 1L,
        title: String = "准备软件杯答辩PPT与演示视频",
        description: String = "包含项目架构图、实机演示和团队分工介绍",
        startTime: Long = 1_700_000_000_000L,
        deadline: Long = startTime + 5L * 24L * 3600_000L,
        isPinned: Boolean = true
    ): Task = Task(
        id = id,
        title = title,
        description = description,
        startTime = startTime,
        deadline = deadline,
        isPinned = isPinned
    )

    @Test
    fun `evaluateTimeHorizon scales suggested step count with urgency and duration`() {
        val now = 1_700_000_000_000L

        val urgentHorizon = SubTaskGenerationSupport.evaluateTimeHorizon(
            startTime = now,
            deadline = now + 2L * 3600_000L,
            nowMillis = now
        )
        assertEquals("2~4", urgentHorizon.suggestedStepRange)
        assertTrue(urgentHorizon.remainingDesc.contains("2小时"))

        val mediumHorizon = SubTaskGenerationSupport.evaluateTimeHorizon(
            startTime = now,
            deadline = now + 24L * 3600_000L,
            nowMillis = now
        )
        assertEquals("3~6", mediumHorizon.suggestedStepRange)

        val longHorizon = SubTaskGenerationSupport.evaluateTimeHorizon(
            startTime = now,
            deadline = now + 7L * 24L * 3600_000L,
            nowMillis = now
        )
        assertEquals("4~8", longHorizon.suggestedStepRange)

        val overdueHorizon = SubTaskGenerationSupport.evaluateTimeHorizon(
            startTime = now - 48L * 3600_000L,
            deadline = now - 3L * 3600_000L,
            nowMillis = now
        )
        assertTrue(overdueHorizon.remainingDesc.contains("已逾期"))
    }

    @Test
    fun `buildSystemPrompt injects full task metadata, existing subtasks, route, and refinement`() {
        val task = sampleTask()
        val existing = listOf(
            SubTask(id = 2L, taskId = 1L, content = "录制App实机操作录屏", isCompleted = false, sortOrder = 1),
            SubTask(id = 1L, taskId = 1L, content = "整理答辩PPT目录大纲", isCompleted = true, sortOrder = 0)
        )
        val context = SubTaskGenerationContext(
            task = task,
            categoryName = "竞赛项目",
            existingSubTasks = existing,
            routeDestination = "信息学院302答辩室（校园东区）",
            attachmentNames = listOf("评分标准.pdf"),
            userProfileSummary = "习惯晚间高效推进",
            additionalContext = "控制总演讲时间在8分钟内",
            mode = SubTaskGenerationMode.APPEND_NEXT,
            previousGeneratedSteps = listOf("编写讲稿并卡点计时"),
            refinementFeedback = "把计时演练再拆成具体两步",
            hasImages = true
        )

        val prompt = SubTaskGenerationSupport.buildSystemPrompt(context, nowMillis = task.startTime + 3600_000L)

        assertTrue(prompt.contains("竞赛项目"))
        assertTrue(prompt.contains("信息学院302答辩室"))
        assertTrue(prompt.contains("评分标准.pdf"))
        assertTrue(prompt.contains("整理答辩PPT目录大纲"))
        assertTrue(prompt.contains("录制App实机操作录屏"))
        assertTrue(prompt.contains("控制总演讲时间在8分钟内"))
        assertTrue(prompt.contains("把计时演练再拆成具体两步"))
        assertTrue(prompt.contains("8~28 个字"))
        assertFalse(prompt.contains("不超过 15 个字"))
    }

    @Test
    fun `parseAndCleanSubTasks extracts embedded JSON, strips numbering, and deduplicates against existing`() {
        val raw = """
            好的，以下是为您拆解后续可执行的步骤：
            ```json
            [
              "1. [✓] 整理答辩PPT目录大纲",
              "录制 App 实机操作录屏！",
              "2. 根据评分标准补充架构图与性能对比页",
              "进行全流程卡点计时演练并压缩冗余页",
              "进行全流程卡点计时演练并压缩冗余页",
              "导出PDF与MP4双备份拷贝至U盘"
            ]
            ```
            希望这些步骤对您有帮助！
        """.trimIndent()

        val existing = listOf(
            SubTask(id = 1L, taskId = 1L, content = "整理答辩PPT目录大纲", isCompleted = true, sortOrder = 0),
            SubTask(id = 2L, taskId = 1L, content = "录制App实机操作录屏", isCompleted = false, sortOrder = 1)
        )

        val appendResult = SubTaskGenerationSupport.parseAndCleanSubTasks(
            rawContent = raw,
            existingSubTasks = existing,
            mode = SubTaskGenerationMode.APPEND_NEXT
        )
        assertTrue(appendResult.isSuccess)
        assertEquals(
            listOf(
                "根据评分标准补充架构图与性能对比页",
                "进行全流程卡点计时演练并压缩冗余页",
                "导出PDF与MP4双备份拷贝至U盘"
            ),
            appendResult.getOrNull()
        )

        // 在 REFINE_ALL 模式下仅过滤已完成步骤，保留重写后的未完成步骤
        val refineResult = SubTaskGenerationSupport.parseAndCleanSubTasks(
            rawContent = raw,
            existingSubTasks = existing,
            mode = SubTaskGenerationMode.REFINE_ALL
        )
        assertTrue(refineResult.isSuccess)
        assertEquals(4, refineResult.getOrNull()?.size)
        assertTrue(refineResult.getOrNull()?.first()?.contains("录制") == true)
    }

    @Test
    fun `parseAndCleanSubTasks supports fallback plain text lines and JSON object arrays`() {
        val jsonObjRaw = """[{"step":"1. 联系指导老师预约预答辩时间"},{"content":"收集评委常见提问并准备Q&A话术"}]"""
        val objResult = SubTaskGenerationSupport.parseAndCleanSubTasks(jsonObjRaw)
        assertTrue(objResult.isSuccess)
        assertEquals(
            listOf("联系指导老师预约预答辩时间", "收集评委常见提问并准备Q&A话术"),
            objResult.getOrNull()
        )

        val plainTextRaw = """
            建议步骤如下：
            1、核对设备接口与HDMI转接头是否可用
            - 提前半小时到达信息学院302试放PPT与视频
        """.trimIndent()
        val plainResult = SubTaskGenerationSupport.parseAndCleanSubTasks(plainTextRaw)
        assertTrue(plainResult.isSuccess)
        assertEquals(
            listOf("核对设备接口与HDMI转接头是否可用", "提前半小时到达信息学院302试放PPT与视频"),
            plainResult.getOrNull()
        )
    }

    @Test
    fun `parseAndCleanSubTasks returns descriptive failure when all items duplicate existing subtasks`() {
        val raw = """["整理答辩PPT目录大纲", "录制App实机操作录屏"]"""
        val existing = listOf(
            SubTask(id = 1L, taskId = 1L, content = "整理答辩PPT目录大纲", isCompleted = true, sortOrder = 0),
            SubTask(id = 2L, taskId = 1L, content = "录制App实机操作录屏", isCompleted = false, sortOrder = 1)
        )
        val result = SubTaskGenerationSupport.parseAndCleanSubTasks(raw, existing, SubTaskGenerationMode.APPEND_NEXT)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("高度重复") == true)
    }
}
