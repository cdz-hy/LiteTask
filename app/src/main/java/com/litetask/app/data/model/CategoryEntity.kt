package com.litetask.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
@Entity(tableName = "categories")
data class Category(
    @SerializedName("id", alternate = ["a"])
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("name", alternate = ["b"])
    @ColumnInfo(name = "name")
    val name: String,

    @SerializedName("color_hex", alternate = ["c"])
    @ColumnInfo(name = "color_hex")
    val colorHex: String,

    @SerializedName("icon_name", alternate = ["d"])
    @ColumnInfo(name = "icon_name")
    val iconName: String = "default", // 预留字段，用于图标

    @SerializedName("is_default", alternate = ["e"])
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false // 是否为预置分类（不可删除）
)
