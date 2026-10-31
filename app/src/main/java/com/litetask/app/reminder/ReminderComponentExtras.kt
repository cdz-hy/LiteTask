package com.litetask.app.reminder

import android.content.Intent
import com.litetask.app.data.model.AMapRouteData
import com.litetask.app.data.model.AccessPassData
import com.litetask.app.data.model.LinkAndPassSupport
import com.litetask.app.data.model.LinkBookmarkData
import com.litetask.app.data.model.TaskComponent

/**
 * 强提醒表面（系统通知 / 全屏锁屏 ReminderActivity / 悬浮窗）共享的组件快捷上下文载体。
 * 统一封装「取件码 / 会议号 / 票务核销」「目的地导航」「深链接直达」的 Intent 序列化与还原。
 */
data class ReminderComponentExtras(
    val highlightLine: String? = null,
    val passType: String? = null,
    val passTitle: String? = null,
    val passCode: String? = null,
    val passSecret: String? = null,
    val routeName: String? = null,
    val routeAddress: String? = null,
    val routeLat: Double? = null,
    val routeLng: Double? = null,
    val routeAdcode: String? = null,
    val linkTitle: String? = null,
    val linkUrl: String? = null
) {
    val hasPass: Boolean get() = !passCode.isNullOrBlank()
    val hasRoute: Boolean get() = !routeName.isNullOrBlank() && routeLat != null && routeLng != null
    val hasLink: Boolean get() = !linkUrl.isNullOrBlank()
    val hasAnyContent: Boolean get() = hasPass || hasRoute || hasLink || !highlightLine.isNullOrBlank()

    fun toAccessPassData(): AccessPassData? {
        val code = passCode?.takeIf { it.isNotBlank() } ?: return null
        return AccessPassData(
            passType = passType ?: LinkAndPassSupport.AccessPassType.OTHER.code,
            title = passTitle?.takeIf { it.isNotBlank() } ?: LinkAndPassSupport.AccessPassType.fromCode(passType).label,
            primaryCode = code,
            secondarySecret = passSecret?.takeIf { it.isNotBlank() }
        )
    }

    fun toAMapRouteData(): AMapRouteData? {
        val name = routeName?.takeIf { it.isNotBlank() } ?: return null
        val lat = routeLat ?: return null
        val lng = routeLng ?: return null
        return AMapRouteData(
            endName = name,
            endAddress = routeAddress,
            endLat = lat,
            endLng = lng,
            adcode = routeAdcode
        )
    }

    fun toLinkBookmarkData(): LinkBookmarkData? {
        val url = linkUrl?.takeIf { it.isNotBlank() } ?: return null
        return LinkBookmarkData(
            url = url,
            title = linkTitle?.takeIf { it.isNotBlank() }
        )
    }

    fun putToIntent(intent: Intent) {
        highlightLine?.let { intent.putExtra(KEY_HIGHLIGHT_LINE, it) }
        passType?.let { intent.putExtra(KEY_PASS_TYPE, it) }
        passTitle?.let { intent.putExtra(KEY_PASS_TITLE, it) }
        passCode?.let { intent.putExtra(KEY_PASS_CODE, it) }
        passSecret?.let { intent.putExtra(KEY_PASS_SECRET, it) }
        routeName?.let { intent.putExtra(KEY_ROUTE_NAME, it) }
        routeAddress?.let { intent.putExtra(KEY_ROUTE_ADDRESS, it) }
        if (routeLat != null && routeLng != null) {
            intent.putExtra(KEY_ROUTE_LAT, routeLat)
            intent.putExtra(KEY_ROUTE_LNG, routeLng)
        }
        routeAdcode?.let { intent.putExtra(KEY_ROUTE_ADCODE, it) }
        linkTitle?.let { intent.putExtra(KEY_LINK_TITLE, it) }
        linkUrl?.let { intent.putExtra(KEY_LINK_URL, it) }
    }

    companion object {
        private const val KEY_HIGHLIGHT_LINE = "comp_highlight_line"
        private const val KEY_PASS_TYPE = "comp_pass_type"
        private const val KEY_PASS_TITLE = "comp_pass_title"
        private const val KEY_PASS_CODE = "comp_pass_code"
        private const val KEY_PASS_SECRET = "comp_pass_secret"
        private const val KEY_ROUTE_NAME = "comp_route_name"
        private const val KEY_ROUTE_ADDRESS = "comp_route_address"
        private const val KEY_ROUTE_LAT = "comp_route_lat"
        private const val KEY_ROUTE_LNG = "comp_route_lng"
        private const val KEY_ROUTE_ADCODE = "comp_route_adcode"
        private const val KEY_LINK_TITLE = "comp_link_title"
        private const val KEY_LINK_URL = "comp_link_url"

        fun fromComponents(components: List<TaskComponent>): ReminderComponentExtras? {
            if (components.isEmpty()) return null
            val summary = LinkAndPassSupport.extractReminderSummary(components)
            val extras = ReminderComponentExtras(
                highlightLine = summary.highlightLine,
                passType = summary.primaryPass?.passType,
                passTitle = summary.primaryPass?.title,
                passCode = summary.primaryPass?.primaryCode,
                passSecret = summary.primaryPass?.secondarySecret,
                routeName = summary.primaryRoute?.endName,
                routeAddress = summary.primaryRoute?.endAddress,
                routeLat = summary.primaryRoute?.endLat,
                routeLng = summary.primaryRoute?.endLng,
                routeAdcode = summary.primaryRoute?.adcode,
                linkTitle = summary.primaryLink?.title,
                linkUrl = summary.primaryLink?.url
            )
            return extras.takeIf { it.hasAnyContent }
        }

        fun fromIntent(intent: Intent?): ReminderComponentExtras? {
            if (intent == null) return null
            val hasLatLng = intent.hasExtra(KEY_ROUTE_LAT) && intent.hasExtra(KEY_ROUTE_LNG)
            val extras = ReminderComponentExtras(
                highlightLine = intent.getStringExtra(KEY_HIGHLIGHT_LINE),
                passType = intent.getStringExtra(KEY_PASS_TYPE),
                passTitle = intent.getStringExtra(KEY_PASS_TITLE),
                passCode = intent.getStringExtra(KEY_PASS_CODE),
                passSecret = intent.getStringExtra(KEY_PASS_SECRET),
                routeName = intent.getStringExtra(KEY_ROUTE_NAME),
                routeAddress = intent.getStringExtra(KEY_ROUTE_ADDRESS),
                routeLat = if (hasLatLng) intent.getDoubleExtra(KEY_ROUTE_LAT, 0.0) else null,
                routeLng = if (hasLatLng) intent.getDoubleExtra(KEY_ROUTE_LNG, 0.0) else null,
                routeAdcode = intent.getStringExtra(KEY_ROUTE_ADCODE),
                linkTitle = intent.getStringExtra(KEY_LINK_TITLE),
                linkUrl = intent.getStringExtra(KEY_LINK_URL)
            )
            return extras.takeIf { it.hasAnyContent }
        }
    }
}
