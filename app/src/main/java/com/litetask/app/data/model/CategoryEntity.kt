package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color_hex")
    val colorHex: String,

    @ColumnInfo(name = "icon_name")
    val iconName: String = "default", // 预留字段，用于图标

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false // 是否为预置分类（不可删除）
)
