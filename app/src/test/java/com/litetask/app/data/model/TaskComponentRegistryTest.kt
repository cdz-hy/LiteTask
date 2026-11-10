package com.litetask.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskComponentRegistryTest {

    @Test
    fun `TempComponentIdGenerator emits unique negative IDs and toEntity normalizes them to zero`() {
        val ids = (1..20).map { TempComponentIdGenerator.nextId() }
        assertTrue(ids.all { it < 0L })
        assertEquals(20, ids.toSet().size)

        val tempComp = TaskComponent.AMapComponent(
            id = ids.first(),
            taskId = 0L,
            data = AMapRouteData(
                endName = "中关村软件园",
                endAddress = "海淀区西北旺",
                endLat = 40.049,
                endLng = 116.298
            )
        )
        assertEquals(0L, tempComp.toEntity(taskId = 42L).id)

        val persistedComp = tempComp.copy(id = 88L, taskId = 42L)
        assertEquals(88L, persistedComp.toEntity(taskId = 42L).id)
    }

    @Test
    fun `TaskTypeConverter and malformed payload degrade gracefully to UnknownComponent`() {
        val converter = TaskTypeConverter()
        assertEquals(ComponentType.UNKNOWN, converter.toComponentType("FUTURE_PLUGIN_V9"))
        assertEquals(ComponentType.AMAP_ROUTE, converter.toComponentType("AMAP_ROUTE"))
        assertNull(converter.toComponentType(null))

        val corruptEntity = TaskComponentEntity(
            id = 7L,
            taskId = 10L,
            type = ComponentType.AMAP_ROUTE,
            dataJson = "{invalid-json"
        )
        val missingRequiredField = TaskComponentEntity(
            id = 8L,
            taskId = 10L,
            type = ComponentType.FILE_ATTACHMENT,
            dataJson = """{"file_name":"","file_uri":"content://x","mime_type":"*/*","file_size":1}"""
        )
        val validEntity = TaskComponent.FileComponent(
            id = 9L,
            taskId = 10L,
            data = FileAttachmentData("需求文档.pdf", "content://doc/9", "application/pdf", 2048L)
        ).toEntity(taskId = 10L)

        val decoded = listOf(corruptEntity, missingRequiredField, validEntity).toTaskComponentsSafe()
        assertTrue(decoded[0] is TaskComponent.UnknownComponent)
        assertTrue(decoded[1] is TaskComponent.UnknownComponent)
        assertTrue(decoded[2] is TaskComponent.FileComponent)
        assertEquals("需求文档.pdf", (decoded[2] as TaskComponent.FileComponent).data.fileName)
    }

    @Test
    fun `computeIncrementalDiff retains unchanged id and createdAt while classifying updates inserts deletes`() {
        val untouched = TaskComponent.FileComponent(
            id = 1L,
            taskId = 50L,
            data = FileAttachmentData("保留.txt", "content://1", "text/plain", 100L)
        ).toEntity(taskId = 50L, createdAt = 1111L)

        val toBeModified = TaskComponent.AMapComponent(
            id = 2L,
            taskId = 50L,
            data = AMapRouteData(endName = "旧终点", endAddress = "旧地址", endLat = 39.9, endLng = 116.3)
        ).toEntity(taskId = 50L, createdAt = 2222L)

        val toBeRemoved = TaskComponent.FileComponent(
            id = 3L,
            taskId = 50L,
            data = FileAttachmentData("删除.png", "content://3", "image/png", 500L)
        ).toEntity(taskId = 50L, createdAt = 3333L)

        val updatedRoute = TaskComponent.AMapComponent(
            id = 2L,
            taskId = 50L,
            data = AMapRouteData(endName = "西二旗地铁站", endAddress = "昌平线", endLat = 40.05, endLng = 116.31)
        )
        val newlyAddedFile = TaskComponent.FileComponent(
            id = TempComponentIdGenerator.nextId(),
            taskId = 50L,
            data = FileAttachmentData("新增.pdf", "content://new", "application/pdf", 4096L)
        )

        val diff = TaskComponentRegistry.computeIncrementalDiff(
            existingEntities = listOf(untouched, toBeModified, toBeRemoved),
            targetComponents = listOf(untouched.toTaskComponent(), updatedRoute, newlyAddedFile),
            taskId = 50L
        )

        assertEquals(listOf(3L), diff.toDelete.map { it.id })
        assertEquals(1, diff.toUpdate.size)
        assertEquals(2L, diff.toUpdate.first().id)
        assertEquals(2222L, diff.toUpdate.first().createdAt)
        assertTrue(diff.toUpdate.first().dataJson.contains("西二旗地铁站"))
        assertEquals(1, diff.toInsert.size)
        assertEquals(0L, diff.toInsert.first().id)
        assertTrue(diff.toInsert.first().dataJson.contains("新增.pdf"))
    }

    @Test
    fun `extractAddedOrChangedRoutes only returns genuinely new or changed destinations`() {
        val routeA = TaskComponent.AMapComponent(
            id = 1L,
            taskId = 5L,
            data = AMapRouteData(endName = "国家图书馆", endAddress = "中关村南大街33号", endLat = 39.94, endLng = 116.32)
        )
        val routeB = TaskComponent.AMapComponent(
            id = 2L,
            taskId = 5L,
            data = AMapRouteData(endName = "奥林匹克公园", endAddress = "朝阳区", endLat = 40.00, endLng = 116.39)
        )

        val delta = TaskComponentRegistry.extractAddedOrChangedRoutes(
            oldComponents = listOf(routeA),
            newComponents = listOf(routeA.copy(id = 10L), routeB)
        )
        assertEquals(listOf("奥林匹克公园"), delta.map { it.data.endName })
    }

    @Test
    fun `matchesSearchQuery searches payload values cleanly without false-positive JSON key hits`() {
        val routeEntity = TaskComponent.AMapComponent(
            id = 1L,
            taskId = 100L,
            data = AMapRouteData(endName = "北京南站", endAddress = "丰台区永外大街", endLat = 39.865, endLng = 116.378)
        ).toEntity(taskId = 100L)
        val fileEntity = TaskComponent.FileComponent(
            id = 2L,
            taskId = 100L,
            data = FileAttachmentData("车票报销单.pdf", "content://media/external/file/99", "application/pdf", 1024L)
        ).toEntity(taskId = 100L)

        val composite = TaskDetailComposite(
            task = Task(
                id = 100L,
                title = "周末出差返程",
                type = TaskType.WORK,
                startTime = 1_000L,
                deadline = 2_000L
            ),
            subTasks = emptyList(),
            reminders = emptyList(),
            category = null,
            components = listOf(routeEntity, fileEntity)
        )

        assertTrue(TaskComponentRegistry.matchesSearchQuery(composite, "北京南站"))
        assertTrue(TaskComponentRegistry.matchesSearchQuery(composite, "报销单"))
        assertFalse(TaskComponentRegistry.matchesSearchQuery(composite, "end_lat"))
        assertFalse(TaskComponentRegistry.matchesSearchQuery(composite, "content://media"))
    }

    @Test
    fun `extractCalendarLocation and formatSizeSafe handle boundaries cleanly`() {
        val route = TaskComponent.AMapComponent(
            id = 1L,
            taskId = 1L,
            data = AMapRouteData(endName = "天坛公园", endAddress = "东城区天坛东里甲1号", endLat = 39.88, endLng = 116.41)
        )
        val file = TaskComponent.FileComponent(
            id = 2L,
            taskId = 1L,
            data = FileAttachmentData("导览图.jpg", "content://1", "image/jpeg", 2048L)
        )

        assertEquals("天坛公园 (东城区天坛东里甲1号)", TaskComponentRegistry.extractCalendarLocation(listOf(route, file)))
        assertEquals(
            listOf("目的地: 天坛公园 (东城区天坛东里甲1号) [驾车]", "附件: 导览图.jpg (2 KB)"),
            TaskComponentRegistry.formatCalendarDescriptionLines(listOf(route, file))
        )

        assertEquals("0 B", TaskComponentRegistry.formatSizeSafe(0L))
        assertEquals("0 B", TaskComponentRegistry.formatSizeSafe(-10L))
        assertEquals("1 KB", TaskComponentRegistry.formatSizeSafe(1024L))
        // 极端边界 Long.MAX_VALUE 不应抛出下标越界异常
        assertTrue(TaskComponentRegistry.formatSizeSafe(Long.MAX_VALUE).endsWith("TB"))
    }
}
