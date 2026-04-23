package com.litetask.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep

/**
 * 核心画像快照表
 * 采用 Append-Only（只追加）模式记录每一次生成的画像结果
 */
@Keep
@Entity(tableName = "user_profile_history")
data class UserProfileHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // ==================== 客观数据 ====================
    // (由 App 在后台执行 SQL 计算后直接写入)

    @ColumnInfo(name = "completion_rate")
    val completionRate: Double, // 任务总体完成率

    @ColumnInfo(name = "delayed_rate")
    val delayedRate: Double, // 拖延/逾期率

    @ColumnInfo(name = "input_preference")
    val inputPreference: String? = null, // 输入偏好 (如 "VOICE", "TEXT")

    @ColumnInfo(name = "category_focus")
    val categoryFocus: String? = null, // 分类重心 (如 "WORK", "LIFE")

    // ==================== 主观数据 ====================
    // (由 LLM 结合上述客观数据及近期任务文本推理后写入)

    @ColumnInfo(name = "identity")
    val identity: String? = null, // 用户身份

    @ColumnInfo(name = "industry")
    val industry: String? = null, // 所属行业

    @ColumnInfo(name = "travel_preference")
    val travelPreference: String? = null, // 出行偏好

    @ColumnInfo(name = "delay_index")
    val delayIndex: Int = 1, // 拖延指数 (1-5级)

    @ColumnInfo(name = "plan_ability")
    val planAbility: Int = 1, // 规划能力 (1-5级)

    @ColumnInfo(name = "stress_level")
    val stressLevel: Int = 1, // 压力承受度 (1-5级)

    @ColumnInfo(name = "execution_rhythm")
    val executionRhythm: String? = null, // 执行节奏

    @ColumnInfo(name = "ai_tone")
    val aiTone: String? = null, // AI 语气偏好

    @ColumnInfo(name = "personality")
    val personality: String? = null // 综合性格特征描述
)
