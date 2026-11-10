package com.litetask.app.data.model

import com.litetask.app.data.ai.AiTaskComponentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAndPassSupportTest {

    @Test
    fun extractLinkFromSharedText_stripsShareCopyNoiseAndDetectsBrand() {
        val sample = "【腾讯文档】需求评审纪要 https://docs.qq.com/doc/DRk123456，"
        val link = LinkAndPassSupport.extractLinkFromSharedText(sample)

        assertNotNull(link)
        assertEquals("https://docs.qq.com/doc/DRk123456", link!!.url)
        assertEquals("需求评审纪要", link.title)
        assertEquals("DOC", link.linkCategory)

        val gh = LinkAndPassSupport.resolveBrandMeta("github.com/google/guava)")
        assertEquals("GitHub", gh.brandName)
        assertEquals(LinkAndPassSupport.LinkCategory.CODE, gh.category)

        val deep = LinkAndPassSupport.resolveBrandMeta("wemeet://page/launch?meeting_code=123456")
        assertEquals(LinkAndPassSupport.LinkCategory.DEEPLINK, deep.category)
    }

    @Test
    fun extractPassesFallbackFromText_extractsMeetingPickupTrainAndVerify() {
        val msg = """
            【菜鸟驿站】您的包裹已到达，请凭取件码: 3-2-104 及时领取。
            下午3点腾讯会议：882 419 021，入会密码：2026，链接 https://meeting.tencent.com/dm/abc123
            周六 G1234 次列车 05车12A座 检票口A12
            线下核销码：XY9988
        """.trimIndent()

        val passes = LinkAndPassSupport.extractPassesFallbackFromText(msg)
        val meeting = passes.firstOrNull { it.passType == "MEETING" }
        val pickup = passes.firstOrNull { it.passType == "PICKUP" }
        val ticket = passes.firstOrNull { it.passType == "TICKET" }
        val verify = passes.firstOrNull { it.passType == "VERIFY" }

        assertNotNull(meeting)
        assertEquals("882 419 021", meeting!!.primaryCode)
        assertEquals("密码 2026", meeting.secondarySecret)
        assertEquals("https://meeting.tencent.com/dm/abc123", meeting.actionUrl)

        assertNotNull(pickup)
        assertEquals("3-2-104", pickup!!.primaryCode)

        assertNotNull(ticket)
        assertTrue(ticket!!.primaryCode.contains("G1234"))
        assertTrue(ticket.primaryCode.contains("05车12A"))
        assertEquals("检票口A12", ticket.secondarySecret)

        assertNotNull(verify)
        assertEquals("XY9988", verify!!.primaryCode)
    }

    @Test
    fun aiTaskComponentParser_parsesCamelAndSnakeCaseExtrasAndDeduplicates() {
        val task = Task(
            title = "评审需求",
            type = TaskType.WORK,
            startTime = 1_000L,
            deadline = 2_000L
        )
        val json = """
            {
              "links": [
                {"url": "https://github.com/org/repo", "title": "仓库PR", "category": "CODE"},
                {"url": "https://github.com/org/repo", "title": "重复"}
              ],
              "passes": [
                {"pass_type": "MEETING", "title": "产品评审", "primary_code": "123 456 789", "secondary_secret": "9527"}
              ]
            }
        """.trimIndent()

        AiTaskComponentParser.applyParsedExtras(task, json)

        assertEquals(1, task.parsedLinks.size)
        assertEquals("仓库PR", task.parsedLinks.first().title)
        assertEquals(1, task.parsedPasses.size)
        assertEquals("123 456 789", task.parsedPasses.first().primaryCode)
        assertEquals("9527", task.parsedPasses.first().secondarySecret)
    }

    @Test
    fun componentRegistryAndReminderSummary_handleLinksPassesAndRoundTrip() {
        val pass = TaskComponent.AccessPassComponent(
            id = 11L,
            taskId = 99L,
            data = AccessPassData(
                passType = "PICKUP",
                title = "菜鸟驿站",
                primaryCode = "8-1-204",
                secondarySecret = "柜门2"
            )
        )
        val link = TaskComponent.LinkComponent(
            id = 12L,
            taskId = 99L,
            data = LinkBookmarkData(
                url = "https://bilibili.com/video/BV1xx",
                title = "参考视频"
            )
        )
        val route = TaskComponent.AMapComponent(
            id = 13L,
            taskId = 99L,
            data = AMapRouteData(
                endName = "国家图书馆",
                endAddress = "海淀区中关村南大街33号",
                endLat = 39.94,
                endLng = 116.32,
                estimatedDurationSec = 1200
            )
        )

        val entity = pass.toEntity(99L)
        val restored = entity.toTaskComponent()
        assertTrue(restored is TaskComponent.AccessPassComponent)
        assertTrue(TaskComponentRegistry.chipSummary(pass).contains("8-1-204"))
        assertTrue(TaskComponentRegistry.searchableText(link).contains("bilibili.com"))
        assertNull(link.toEntity(99L).let { null }) // smoke check

        val summaryWithPass = LinkAndPassSupport.extractReminderSummary(listOf(route, link, pass))
        assertEquals("8-1-204", summaryWithPass.primaryPass?.primaryCode)
        assertNotNull(summaryWithPass.primaryRoute)
        assertNotNull(summaryWithPass.primaryLink)
        assertTrue(summaryWithPass.highlightLine!!.contains("菜鸟驿站 8-1-204"))

        val summaryRouteOnly = LinkAndPassSupport.extractReminderSummary(listOf(route))
        assertTrue(summaryRouteOnly.highlightLine!!.contains("国家图书馆"))
    }
}
