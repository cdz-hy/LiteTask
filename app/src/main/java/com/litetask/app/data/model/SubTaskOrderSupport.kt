package com.litetask.app.data.model

/**
 * 子任务排序与重排辅助工具。
 *
 * 约定：优先按用户拖放调整后的 sortOrder 升序；若遇到历史数据（sortOrder 均为 0）则以 id 升序兜底，
 * 确保升级后原有展示顺序稳定不乱跳。
 */
val SUBTASK_ORDER_COMPARATOR: Comparator<SubTask> =
    compareBy<SubTask> { it.sortOrder }.thenBy { it.id }

fun List<SubTask>.sortedByOrder(): List<SubTask> = sortedWith(SUBTASK_ORDER_COMPARATOR)

/**
 * 在已有序列基础上安全移动元素，并返回重新编号后的有序列表（sortOrder = 0..lastIndex）。
 */
fun List<SubTask>.moveSubTaskItem(fromIndex: Int, toIndex: Int): List<SubTask> {
    if (fromIndex == toIndex) return this
    if (fromIndex !in indices || toIndex !in indices) return this
    val mutable = toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable.mapIndexed { index, subTask ->
        if (subTask.sortOrder == index) subTask else subTask.copy(sortOrder = index)
    }
}

/**
 * 将混合 LazyColumn（前置 header + 子任务列表 + 后置 footer）中的拖拽索引安全换算为子任务下标。
 */
fun resolveSubTaskMoveIndices(
    fromLazyIndex: Int,
    toLazyIndex: Int,
    headerCount: Int,
    subTaskCount: Int
): Pair<Int, Int>? {
    if (subTaskCount <= 1 || fromLazyIndex == toLazyIndex) return null
    val fromSub = fromLazyIndex - headerCount
    val toSub = toLazyIndex - headerCount
    if (fromSub !in 0 until subTaskCount || toSub !in 0 until subTaskCount) return null
    return fromSub to toSub
}
