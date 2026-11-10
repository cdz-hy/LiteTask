package com.litetask.app.data.model

import com.google.gson.Gson
import com.litetask.app.data.model.AMapRouteSupport.resolvedDrivingStrategy
import com.litetask.app.data.model.AMapRouteSupport.resolvedTravelMode
import com.litetask.app.data.repository.AMapRepository
import com.litetask.app.data.repository.AMapWeatherForecastDay
import com.litetask.app.data.repository.AMapWeatherReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AMapRouteSupportTest {

    @Test
    fun `legacy AMapRouteData JSON without Round2 fields decodes safely with DRIVING default`() {
        val legacyJson = """
            {"start_name":"我的位置","end_name":"故宫博物院","end_address":"东城区景山前街4号","end_lat":39.918,"end_lng":116.397,"adcode":"110101","strategy":2}
        """.trimIndent()
        val parsed = Gson().fromJson(legacyJson, AMapRouteData::class.java)
        assertEquals("故宫博物院", parsed.endName)
        assertNull(parsed.startLat)
        assertNull(parsed.estimatedDurationSec)
        assertEquals(AMapRouteSupport.TravelMode.DRIVING, parsed.resolvedTravelMode())
        assertEquals(AMapRouteSupport.DrivingStrategy.SHORT_DISTANCE, parsed.resolvedDrivingStrategy())
    }

    @Test
    fun `AMapRepository URL builders enforce bias location, strategy, and reject missing transit city`() {
        val tipsUrl = AMapRepository.buildInputTipsUrl(
            keyword = "万达广场",
            key = "TEST_KEY",
            biasLocation = "121.47,31.23",
            city = "上海"
        )
        assertTrue(tipsUrl.contains("location=121.47%2C31.23"))
        assertTrue(tipsUrl.contains("city=%E4%B8%8A%E6%B5%B7"))

        val drivingUrl = AMapRepository.buildRouteUrl(
            originLng = 116.3,
            originLat = 39.9,
            destLng = 116.4,
            destLat = 40.0,
            mode = "driving",
            key = "TEST_KEY",
            strategy = 1
        )
        assertNotNull(drivingUrl)
        assertTrue(drivingUrl!!.contains("strategy=1"))

        // 公交规划缺城市时不再伪造“北京”，应返回 null
        val invalidTransitUrl = AMapRepository.buildRouteUrl(
            originLng = 121.4,
            originLat = 31.2,
            destLng = 121.5,
            destLat = 31.3,
            mode = "transit",
            key = "TEST_KEY",
            city = null
        )
        assertNull(invalidTransitUrl)

        val validTransitUrl = AMapRepository.buildRouteUrl(
            originLng = 121.4,
            originLat = 31.2,
            destLng = 121.5,
            destLat = 31.3,
            mode = "transit",
            key = "TEST_KEY",
            city = "上海市"
        )
        assertNotNull(validTransitUrl)
        assertTrue(validTransitUrl!!.contains("city=%E4%B8%8A%E6%B5%B7%E5%B8%82"))
    }

    @Test
    fun `parseWeatherForecastResponse and matchForecastForTaskDate align day and night conditions`() {
        val sampleJson = """
            {
              "status": "1",
              "forecasts": [{
                "city": "杭州市",
                "adcode": "330100",
                "reporttime": "2026-11-16 08:00:00",
                "casts": [
                  {"date": "2026-11-16", "week": "1", "dayweather": "晴", "nightweather": "多云", "daytemp": "20", "nighttemp": "11", "daywind": "东", "nightwind": "东"},
                  {"date": "2026-11-17", "week": "2", "dayweather": "小雨", "nightweather": "阴", "daytemp": "17", "nighttemp": "10", "daywind": "北", "nightwind": "北"}
                ]
              }]
            }
        """.trimIndent()

        val report = AMapRepository.parseWeatherForecastResponse(sampleJson)
        assertNotNull(report)
        assertEquals(2, report!!.forecasts.size)

        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        val nowCal = Calendar.getInstance(tz).apply { set(2026, Calendar.NOVEMBER, 16, 10, 0, 0) }
        val tomorrowEvening = Calendar.getInstance(tz).apply { set(2026, Calendar.NOVEMBER, 17, 19, 30, 0) }
        val outOfRange = Calendar.getInstance(tz).apply { set(2026, Calendar.NOVEMBER, 25, 10, 0, 0) }

        val matchedTomorrow = AMapRouteSupport.matchForecastForTaskDate(
            report = report,
            targetTimeMillis = tomorrowEvening.timeInMillis,
            timeZone = tz,
            nowMillis = nowCal.timeInMillis
        )
        assertNotNull(matchedTomorrow)
        assertEquals("明天", matchedTomorrow!!.dayLabel)
        assertEquals("阴", matchedTomorrow.weather)
        assertEquals("10~17°C", matchedTomorrow.tempRange)

        assertNull(
            AMapRouteSupport.matchForecastForTaskDate(
                report = report,
                targetTimeMillis = outOfRange.timeInMillis,
                timeZone = tz,
                nowMillis = nowCal.timeInMillis
            )
        )
    }

    @Test
    fun `departure calculation and multi-map navigation fallback targets work cleanly`() {
        val startMs = 1_700_000_000_000L
        val suggested = AMapRouteSupport.calculateSuggestedDepartureMillis(
            taskStartTime = startMs,
            estimatedDurationSec = 1800, // 30m
            bufferMinutes = 10
        )
        assertEquals(startMs - 40 * 60_000L, suggested)
        assertEquals("约30分钟 · 12.5km", AMapRouteSupport.formatEtaSummary(1800, 12500))

        val data = AMapRouteData(
            startName = "公司",
            startLat = 31.23,
            startLng = 121.47,
            endName = "虹桥火车站",
            endAddress = "闵行区申贵路",
            endLat = 31.194,
            endLng = 121.320,
            travelMode = "transit"
        )
        val targets = AMapRouteSupport.buildMapNavigationTargets(data)
        assertEquals(5, targets.size)
        assertEquals("com.autonavi.minimap", targets[0].packageName)
        assertTrue(targets[0].uriString.contains("t=1"))
        assertTrue(targets[0].uriString.contains("slat=31.23"))
        assertEquals("com.baidu.BaiduMap", targets[1].packageName)
        assertTrue(targets[1].uriString.contains("mode=transit"))
        assertEquals("com.tencent.map", targets[2].packageName)
        assertTrue(targets[2].uriString.contains("type=bus"))
        assertNull(targets[3].packageName)
        assertTrue(targets[3].uriString.startsWith("geo:"))
        assertNull(targets[4].packageName)
        assertTrue(targets[4].uriString.startsWith("https://uri.amap.com/navigation"))
    }
}
