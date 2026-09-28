package com.litetask.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoCoordinateUtilsTest {

    @Test
    fun `wgs84ToGcj02 shifts coordinates inside China by realistic offset`() {
        // 北京天安门附近 WGS-84 坐标
        val wgsLng = 116.3912757
        val wgsLat = 39.906217
        val (gcjLng, gcjLat) = GeoCoordinateUtils.wgs84ToGcj02(wgsLng, wgsLat)

        // 天安门地区 GCJ-02 相对 WGS-84 约偏东 0.006°、偏北 0.0014°（数百米量级）
        val distanceOffset = GeoCoordinateUtils.distanceMeters(wgsLng, wgsLat, gcjLng, gcjLat)
        assertTrue("Offset should be positive inside China, was $distanceOffset m", distanceOffset in 300.0..800.0)
        assertTrue(gcjLng > wgsLng)
        assertTrue(gcjLat > wgsLat)
    }

    @Test
    fun `wgs84ToGcj02 leaves coordinates outside China unchanged`() {
        val londonLng = -0.1276
        val londonLat = 51.5072
        assertFalse(GeoCoordinateUtils.isInChinaBounds(londonLng, londonLat))

        val (outLng, outLat) = GeoCoordinateUtils.wgs84ToGcj02(londonLng, londonLat)
        assertEquals(londonLng, outLng, 1e-9)
        assertEquals(londonLat, outLat, 1e-9)
    }

    @Test
    fun `distanceMeters computes short and medium distances accurately`() {
        // 同一点距离为 0
        assertEquals(0.0, GeoCoordinateUtils.distanceMeters(116.4, 39.9, 116.4, 39.9), 1e-6)

        // 纬度相差 0.001 度约 111 米
        val approx111m = GeoCoordinateUtils.distanceMeters(116.4, 39.900, 116.4, 39.901)
        assertTrue("Expected ~111m, got $approx111m", approx111m in 105.0..118.0)
    }
}
