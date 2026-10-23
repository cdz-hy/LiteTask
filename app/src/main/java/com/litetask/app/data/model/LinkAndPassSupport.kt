package com.litetask.app.data.model

import java.net.URI
import java.util.Locale

/**
 * 「网页/深链接 (LINK_BOOKMARK)」与「凭证/号牌卡 (ACCESS_PASS)」的纯 Kotlin 解析与业务逻辑支撑中心。
 * 保持零 Android 框架依赖，方便 JVM 全覆盖单测。
 */
object LinkAndPassSupport {

    enum class LinkCategory(val code: String, val label: String) {
        WEB("WEB", "网页"),
        CODE("CODE", "代码库"),
        DOC("DOC", "在线文档"),
        VIDEO("VIDEO", "视频"),
        SHOP("SHOP", "电商"),
        DEEPLINK("DEEPLINK", "应用直达");

        companion object {
            fun fromCode(raw: String?): LinkCategory =
                entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: WEB
        }
    }

    enum class AccessPassType(val code: String, val label: String) {
        MEETING("MEETING", "会议号"),
        PICKUP("PICKUP", "取件码"),
        TICKET("TICKET", "车次/票务"),
        VERIFY("VERIFY", "预约核销"),
        SEAT("SEAT", "考场/座位"),
        OTHER("OTHER", "凭证码");

        companion object {
            fun fromCode(raw: String?): AccessPassType =
                entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
        }
    }

    data class BrandVisualMeta(
        val badgeText: String,
        val brandName: String,
        val accentHex: Long,
        val category: LinkCategory
    )

    data class ReminderComponentSummary(
        val primaryPass: AccessPassData? = null,
        val primaryRoute: AMapRouteData? = null,
        val primaryLink: LinkBookmarkData? = null,
        val highlightLine: String? = null
    )

    private val URL_REGEX = Regex(
        """((?:https?://|[a-zA-Z][a-zA-Z0-9+.-]{1,15}://)[^\s\u4e00-\u9fa5"'<>]+)"""
    )
    private val BARE_DOMAIN_REGEX = Regex(
        """\b((?:[a-zA-Z0-9-]+\.)+(?:com|cn|org|net|io|dev|app|edu|gov|me|co|top|vip)(?:/[^\s\u4e00-\u9fa5"'<>]*)?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val TRAILING_PUNCTUATION = charArrayOf(
        ')', ']', '}', '】', '）', '》', '。', '，', ',', '.', ';', '；', ':', '：', '!', '！', '?', '？', '"', '\''
    )

    fun normalizeLaunchUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd(*TRAILING_PUNCTUATION)
        if (trimmed.isBlank()) return ""
        return if (trimmed.contains("://")) trimmed else "https://$trimmed"
    }

    fun extractHostDisplay(rawUrl: String): String {
        val normalized = normalizeLaunchUrl(rawUrl)
        if (normalized.isBlank()) return ""
        return runCatching {
            val uri = URI(normalized)
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
            if (!host.isNullOrBlank()) {
                host
            } else {
                val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
                if (scheme.isNotBlank()) "$scheme://" else normalized
            }
        }.getOrElse { normalized.substringAfter("://").substringBefore('/').ifBlank { normalized } }
    }

    fun resolveBrandMeta(url: String, explicitCategory: String? = null): BrandVisualMeta {
        val normalized = normalizeLaunchUrl(url)
        val scheme = normalized.substringBefore("://", "").lowercase(Locale.ROOT)
        val host = extractHostDisplay(normalized).lowercase(Locale.ROOT)

        val matched = when {
            host.contains("github.com") -> BrandVisualMeta("GH", "GitHub", 0xFF24292F, LinkCategory.CODE)
            host.contains("gitee.com") -> BrandVisualMeta("码", "Gitee", 0xFFC71D23, LinkCategory.CODE)
            host.contains("gitlab") -> BrandVisualMeta("GL", "GitLab", 0xFFFC6D26, LinkCategory.CODE)
            host.contains("docs.qq.com") -> BrandVisualMeta("文", "腾讯文档", 0xFF1E6FFF, LinkCategory.DOC)
            host.contains("feishu.cn") || host.contains("larkoffice.com") ->
                BrandVisualMeta("飞", "飞书文档", 0xFF00D6B9, LinkCategory.DOC)
            host.contains("notion.so") || host.contains("notion.site") ->
                BrandVisualMeta("N", "Notion", 0xFF191919, LinkCategory.DOC)
            host.contains("yuque.com") -> BrandVisualMeta("雀", "语雀", 0xFF00B96B, LinkCategory.DOC)
            host.contains("shimo.im") -> BrandVisualMeta("石", "石墨文档", 0xFF4C596B, LinkCategory.DOC)
            host.contains("bilibili.com") || host.contains("b23.tv") ->
                BrandVisualMeta("B", "B站", 0xFFFB7299, LinkCategory.VIDEO)
            host.contains("youtube.com") || host.contains("youtu.be") ->
                BrandVisualMeta("YT", "YouTube", 0xFFFF0000, LinkCategory.VIDEO)
            host.contains("zhihu.com") -> BrandVisualMeta("知", "知乎", 0xFF0066FF, LinkCategory.DOC)
            host.contains("taobao.com") || host.contains("tmall.com") || host.contains("tb.cn") ->
                BrandVisualMeta("淘", "淘宝", 0xFFFF5000, LinkCategory.SHOP)
            host.contains("jd.com") -> BrandVisualMeta("京", "京东", 0xFFE1251B, LinkCategory.SHOP)
            host.contains("meeting.tencent.com") || scheme == "wemeet" ->
                BrandVisualMeta("会", "腾讯会议", 0xFF006EFF, LinkCategory.DEEPLINK)
            host.contains("zoom.us") || scheme == "zoomus" ->
                BrandVisualMeta("Z", "Zoom", 0xFF2D8CFF, LinkCategory.DEEPLINK)
            scheme.isNotBlank() && scheme != "http" && scheme != "https" ->
                BrandVisualMeta("App", scheme.uppercase(Locale.ROOT).take(3), 0xFF6750A4, LinkCategory.DEEPLINK)
            else -> {
                val badge = host.firstOrNull()?.uppercaseChar()?.toString() ?: "链"
                val category = LinkCategory.fromCode(explicitCategory)
                BrandVisualMeta(badge, host.ifBlank { "网页链接" }, 0xFF0B57D0, category)
            }
        }
        return if (!explicitCategory.isNullOrBlank()) {
            matched.copy(category = LinkCategory.fromCode(explicitCategory))
        } else {
            matched
        }
    }

    /**
     * 从复制来的混合分享文案（如 "【腾讯文档】需求评审 https://docs.qq.com/xxx"）中清洗抽取 URL 与标题。
     */
    fun extractLinkFromSharedText(rawText: String, fallbackTitle: String? = null): LinkBookmarkData? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        val match = URL_REGEX.find(text) ?: BARE_DOMAIN_REGEX.find(text) ?: return null
        val cleanedUrl = normalizeLaunchUrl(match.groupValues[1])
        if (cleanedUrl.isBlank()) return null

        val prefix = text.substring(0, match.range.first)
            .replace(Regex("""[【\[]\s*腾讯文档\s*[】\]]"""), "")
            .replace(Regex("""^[•\-*\d.、\s]+"""), "")
            .trim()
            .trimEnd('-', '|', ':', '：', '/', ' ')
        val suffix = text.substring((match.range.last + 1).coerceAtMost(text.length)).trim()

        val brand = resolveBrandMeta(cleanedUrl)
        val candidateTitle = when {
            !fallbackTitle.isNullOrBlank() -> fallbackTitle.trim()
            prefix.isNotBlank() && prefix.length in 2..60 -> prefix
            suffix.isNotBlank() && suffix.length in 2..40 -> suffix
            else -> brand.brandName
        }

        return LinkBookmarkData(
            url = cleanedUrl,
            title = candidateTitle,
            linkCategory = brand.category.code,
            note = null
        )
    }

    /**
     * 从长文本中抽出所有独立链接（用于 AI 小模型漏写 links 字段时的正则兜底）。
     */
    fun extractAllLinksFromText(rawText: String, maxCount: Int = 5): List<LinkBookmarkData> {
        if (rawText.isBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<LinkBookmarkData>()
        URL_REGEX.findAll(rawText).forEach { m ->
            if (results.size >= maxCount) return@forEach
            val url = normalizeLaunchUrl(m.groupValues[1])
            if (url.isNotBlank() && seen.add(url.lowercase(Locale.ROOT))) {
                val brand = resolveBrandMeta(url)
                results.add(
                    LinkBookmarkData(
                        url = url,
                        title = brand.brandName,
                        linkCategory = brand.category.code
                    )
                )
            }
        }
        return results
    }

    /**
     * 从短信/会议邀请/订单文案中兜底提取常见凭证码（会议号、取件码、车次票务、核销码）。
     */
    fun extractPassesFallbackFromText(rawText: String): List<AccessPassData> {
        if (rawText.isBlank()) return emptyList()
        val results = mutableListOf<AccessPassData>()
        val seenCodes = HashSet<String>()

        // 1. 会议号 + 可选密码
        val meetingRegex = Regex(
            """(腾讯会议|Zoom|飞书会议|钉钉会议|Teams|会议号?)[:：\s#]*(\d[\d\s-]{6,14}\d)"""
        )
        val pwdRegex = Regex("""(?:入会)?(?:密码|口令|pwd|password)[:：\s]*([A-Za-z0-9]{4,8})""", RegexOption.IGNORE_CASE)
        meetingRegex.findAll(rawText).forEach { m ->
            val provider = m.groupValues[1].takeIf { !it.startsWith("会议") } ?: "线上会议"
            val code = m.groupValues[2].trim().replace(Regex("\\s+"), " ")
            val normalizedKey = code.replace(" ", "").replace("-", "")
            if (seenCodes.add("M:$normalizedKey")) {
                val pwd = pwdRegex.find(rawText)?.groupValues?.getOrNull(1)?.let { "密码 $it" }
                val actionUrl = URL_REGEX.find(rawText)?.groupValues?.getOrNull(1)
                    ?.let(::normalizeLaunchUrl)
                    ?.takeIf { it.contains("meeting", ignoreCase = true) || it.contains("zoom", ignoreCase = true) }
                results.add(
                    AccessPassData(
                        passType = AccessPassType.MEETING.code,
                        title = if (provider == "线上会议") "线上会议" else "$provider 会议",
                        primaryCode = code,
                        secondarySecret = pwd,
                        providerName = provider,
                        actionUrl = actionUrl
                    )
                )
            }
        }

        // 2. 快递取件码
        val pickupRegex = Regex(
            """(菜鸟驿站|丰巢快递柜?|蜂巢|妈妈驿站|快递柜|驿站|中通|圆通|申通|韵达|顺丰|京东快递|极兔)?[^\n]{0,12}?(?:取件码|提货码|取货码|取件号)[:：\s#]*([A-Za-z0-9]{1,4}(?:-[A-Za-z0-9]{1,5}){1,3}|[A-Za-z0-9]{4,12})"""
        )
        pickupRegex.findAll(rawText).forEach { m ->
            val provider = m.groupValues[1].trim().ifBlank { "快递驿站" }
            val code = m.groupValues[2].trim()
            if (seenCodes.add("P:${code.uppercase(Locale.ROOT)}")) {
                results.add(
                    AccessPassData(
                        passType = AccessPassType.PICKUP.code,
                        title = "$provider 取件",
                        primaryCode = code,
                        providerName = provider
                    )
                )
            }
        }

        // 3. 高铁/火车车次 + 车厢座位 + 检票口
        val trainRegex = Regex(
            """(?<![A-Za-z0-9])([GCDZTK]\d{1,4})(?=\s*次|[^\n]{0,24}?(?:\d{1,2}\s*车|检票口))"""
        )
        val seatRegex = Regex("""\d{1,2}\s*车\s*\d{1,3}\s*[A-F席号座]?""")
        val gateRegex = Regex("""检票口[:：\s]*[A-Za-z0-9]+""")
        trainRegex.findAll(rawText).forEach { m ->
            val trainNo = m.groupValues[1].uppercase(Locale.ROOT)
            val lineEnd = rawText.indexOf('\n', m.range.last).let { if (it < 0) rawText.length else it }
            val window = rawText.substring(m.range.first, minOf(lineEnd, m.range.first + 48))
            val seat = seatRegex.find(window)?.value?.trim()?.replace(Regex("\\s+"), "").orEmpty()
            val gate = gateRegex.find(window)?.value?.trim().orEmpty()
            val primary = seat.ifBlank { trainNo }
            if ((seat.isNotBlank() || gate.isNotBlank()) && seenCodes.add("T:$trainNo:$primary")) {
                results.add(
                    AccessPassData(
                        passType = AccessPassType.TICKET.code,
                        title = "$trainNo 次列车",
                        primaryCode = if (seat.isNotBlank()) "$trainNo · $seat" else trainNo,
                        secondarySecret = gate.takeIf { it.isNotBlank() },
                        providerName = "12306 / 铁路出行"
                    )
                )
            }
        }

        // 4. 核销码 / 取餐码 / 预约码 / 座位号
        val verifyRegex = Regex(
            """(核销码|取餐码|预约码|入场码|取票码|考场座位|座位号)[:：\s#]*([A-Za-z0-9-]{3,16})"""
        )
        verifyRegex.findAll(rawText).forEach { m ->
            val label = m.groupValues[1]
            val code = m.groupValues[2].trim()
            val passType = if (label.contains("座")) AccessPassType.SEAT else AccessPassType.VERIFY
            if (seenCodes.add("V:${code.uppercase(Locale.ROOT)}")) {
                results.add(
                    AccessPassData(
                        passType = passType.code,
                        title = label,
                        primaryCode = code
                    )
                )
            }
        }

        return results
    }

    /**
     * 从任务组件集合抽取强提醒 / 通知栏展示与一键动作所需的最关键摘要。
     */
    fun extractReminderSummary(components: List<TaskComponent>): ReminderComponentSummary {
        val pass = components.filterIsInstance<TaskComponent.AccessPassComponent>().firstOrNull()?.data
        val route = components.filterIsInstance<TaskComponent.AMapComponent>().firstOrNull()?.data
        val link = components.filterIsInstance<TaskComponent.LinkComponent>().firstOrNull()?.data

        val highlightLine = when {
            pass != null -> buildString {
                val typeLabel = AccessPassType.fromCode(pass.passType).label
                append(pass.title.ifBlank { typeLabel })
                append(" ")
                append(pass.primaryCode)
                pass.secondarySecret?.takeIf { it.isNotBlank() }?.let {
                    append(" · ").append(it)
                }
            }
            route != null -> {
                val eta = AMapRouteSupport.formatDurationShort(route.estimatedDurationSec)
                if (eta != null) "目的地: ${route.endName} (约$eta)" else "目的地: ${route.endName}"
            }
            link != null -> "🔗 ${extractHostDisplay(link.url)}"
            else -> null
        }

        return ReminderComponentSummary(
            primaryPass = pass,
            primaryRoute = route,
            primaryLink = link,
            highlightLine = highlightLine
        )
    }

    fun LinkBookmarkData.effectiveTitle(): String =
        title?.trim()?.takeIf { it.isNotEmpty() } ?: extractHostDisplay(url).ifBlank { url }
}
