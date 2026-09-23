package com.litetask.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubTaskOrderSupportTest {

    @Test
    fun `sortedByOrder prefers sortOrder and falls back to id on ties`() {
        val list = listOf(
            SubTask(id = 12L, taskId = 1L, content = "遗留项2", sortOrder = 0),
            SubTask(id = 10L, taskId = 1L, content = "遗留项1", sortOrder = 0),
            SubTask(id = 5L, taskId = 1L, content = "第二阶段", sortOrder = 2),
            SubTask(id = 8L, taskId = 1L, content = "第一阶段", sortOrder = 1)
        )

        val sorted = list.sortedByOrder()
        assertEquals(listOf(10L, 12L, 8L, 5L), sorted.map { it.id })
    }

    @Test
    fun `moveSubTaskItem reorders elements and normalizes sortOrder`() {
        val initial = listOf(
            SubTask(id = 1L, taskId = 1L, content = "步骤A", isCompleted = true, sortOrder = 0),
            SubTask(id = 2L, taskId = 1L, content = "步骤B", isCompleted = false, sortOrder = 1),
            SubTask(id = 3L, taskId = 1L, content = "步骤C", isCompleted = false, sortOrder = 2)
        )

        // 把最后一项拖到最前面，保持已完成状态与各自 id 不变
        val moved = initial.moveSubTaskItem(fromIndex = 2, toIndex = 0)
        assertEquals(listOf(3L, 1L, 2L), moved.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.sortOrder })
        assertEquals(listOf(false, true, false), moved.map { it.isCompleted })
    }

    @Test
    fun `moveSubTaskItem ignores invalid or noop indices`() {
        val initial = listOf(
            SubTask(id = 1L, taskId = 1L, content = "A", sortOrder = 0),
            SubTask(id = 2L, taskId = 1L, content = "B", sortOrder = 1)
        )

        assertEquals(initial, initial.moveSubTaskItem(0, 0))
        assertEquals(initial, initial.moveSubTaskItem(-1, 1))
        assertEquals(initial, initial.moveSubTaskItem(0, 5))
    }

    @Test
    fun `resolveSubTaskMoveIndices guards header and footer items in LazyColumn`() {
        // header=1 (index 0), subtasks=3 (indices 1..3), footer (index 4)
        assertNull(resolveSubTaskMoveIndices(fromLazyIndex = 1, toLazyIndex = 0, headerCount = 1, subTaskCount = 3))
        assertNull(resolveSubTaskMoveIndices(fromLazyIndex = 0, toLazyIndex = 2, headerCount = 1, subTaskCount = 3))
        assertNull(resolveSubTaskMoveIndices(fromLazyIndex = 2, toLazyIndex = 4, headerCount = 1, subTaskCount = 3))
        assertNull(resolveSubTaskMoveIndices(fromLazyIndex = 2, toLazyIndex = 2, headerCount = 1, subTaskCount = 3))
        assertNull(resolveSubTaskMoveIndices(fromLazyIndex = 1, toLazyIndex = 1, headerCount = 1, subTaskCount = 1))

        assertEquals(0 to 2, resolveSubTaskMoveIndices(fromLazyIndex = 1, toLazyIndex = 3, headerCount = 1, subTaskCount = 3))
        assertEquals(2 to 1, resolveSubTaskMoveIndices(fromLazyIndex = 3, toLazyIndex = 2, headerCount = 1, subTaskCount = 3))
    }
}
