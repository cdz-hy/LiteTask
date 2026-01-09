package com.litetask.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 自定义 AI 闪光图标
 */
val Icons.Filled.AISparkle: ImageVector
    get() {
        if (_aiSparkle != null) {
            return _aiSparkle!!
        }
        _aiSparkle = materialIcon(name = "Filled.AISparkle") {
            materialPath {
                // 主要闪光形状 (大)
                moveTo(11f, 4f)
                quadToRelative(-2f, 6f, -9f, 9f)
                quadToRelative(7f, 3f, 9f, 9f)
                quadToRelative(2f, -6f, 9f, -9f)
                quadToRelative(-7f, -3f, -9f, -9f)
                close()
                
                // 小闪光形状 (右上角)
                moveTo(19f, 3f)
                quadToRelative(-1f, 3f, -4f, 4f)
                quadToRelative(3f, 1f, 4f, 4f)
                quadToRelative(1f, -3f, 4f, -4f)
                quadToRelative(-3f, -1f, -4f, -4f)
                close()
            }
        }
        return _aiSparkle!!
    }

private var _aiSparkle: ImageVector? = null