package com.global_707.drone_scanner.data.airspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 离线包 manifest 解析与校验单测（非法包必须被拒绝，避免"装上了但画不出来"） */
class AirspaceManifestTest {

    private val validJson = """
        {
          "schema": 1,
          "version": "2026-11-02",
          "generatedAt": 1762041600000,
          "tileSize": 256,
          "crs": "GCJ02",
          "zooms": [4, 13],
          "bbox": [73.0, 17.5, 135.5, 54.0],
          "style": { "fill": "#337FC4FF", "stroke": "#CC5AA9E6", "strokeWidth": 2 },
          "attribution": "适飞空域数据，仅供参考",
          "layers": [ { "id": "suitable", "dir": "suitable", "zooms": [6, 13] } ]
        }
    """.trimIndent()

    @Test
    fun `解析合法 manifest`() {
        val manifest = AirspaceManifest.parse(validJson)
        assertEquals(1, manifest.schema)
        assertEquals("2026-11-02", manifest.version)
        assertEquals(256, manifest.tileSize)
        assertEquals(TileCrs.GCJ02, manifest.crs)
        assertEquals(1762041600000L, manifest.generatedAtMillis)
        assertEquals(1, manifest.layers.size)
        val layer = manifest.layer("suitable")!!
        assertEquals("suitable", layer.dir)
        assertEquals(6..13, layer.zooms)
        assertNotNull(manifest.bbox)
        assertEquals(73.0, manifest.bbox!!.minLng, 1e-9)
        assertEquals(0x337FC4FF, manifest.style!!.fillColor)
        assertEquals(0xCC5AA9E6.toInt(), manifest.style!!.strokeColor)
    }

    @Test
    fun `层未给 zooms 时回落到包级 zooms`() {
        val json = """
            {"schema":1,"version":"v1","crs":"GCJ02","zooms":[5,12],
             "layers":[{"id":"suitable","dir":"suitable"}]}
        """.trimIndent()
        assertEquals(5..12, AirspaceManifest.parse(json).layer("suitable")!!.zooms)
    }

    @Test
    fun `缺 version 或 schema 不支持时拒绝`() {
        expectInvalid("""{"schema":1,"layers":[{"id":"suitable","dir":"suitable","zooms":[4,13]}]}""")
        expectInvalid("""{"schema":2,"version":"v1","layers":[{"id":"suitable","dir":"suitable","zooms":[4,13]}]}""")
    }

    @Test
    fun `非法 crs 或 tileSize 时拒绝`() {
        expectInvalid(validJson.replace("\"GCJ02\"", "\"BD09\""))
        expectInvalid(validJson.replace("\"tileSize\": 256", "\"tileSize\": 512"))
    }

    @Test
    fun `layer id 与目录非法时拒绝`() {
        expectInvalid(validJson.replace("\"id\": \"suitable\"", "\"id\": \"Suitable\""))
        expectInvalid(validJson.replace("\"dir\": \"suitable\"", "\"dir\": \"../escape\""))
        expectInvalid(validJson.replace("\"dir\": \"suitable\"", "\"dir\": \"/abs\""))
    }

    @Test
    fun `zooms 反向或越界时拒绝`() {
        expectInvalid(validJson.replace("[6, 13]", "[13, 6]"))
        expectInvalid(validJson.replace("[6, 13]", "[0, 23]"))
        expectInvalid(validJson.replace("[6, 13]", "[4]"))
    }

    @Test
    fun `图层 id 重复时拒绝`() {
        val json = """
            {"schema":1,"version":"v1","crs":"GCJ02",
             "layers":[{"id":"suitable","dir":"a","zooms":[4,13]},
                       {"id":"suitable","dir":"b","zooms":[4,13]}]}
        """.trimIndent()
        expectInvalid(json)
    }

    @Test
    fun `非法 bbox 或颜色时拒绝`() {
        expectInvalid(validJson.replace("[73.0, 17.5, 135.5, 54.0]", "[73.0, 54.0, 135.5, 17.5]"))
        expectInvalid(validJson.replace("\"#337FC4FF\"", "\"blue\""))
    }

    @Test
    fun `相对路径安全检查`() {
        assertTrue(AirspaceManifest.isSafeRelativePath("suitable"))
        assertTrue(AirspaceManifest.isSafeRelativePath("suitable/4"))
        assertFalse(AirspaceManifest.isSafeRelativePath("/etc/passwd"))
        assertFalse(AirspaceManifest.isSafeRelativePath("C:/x"))
        assertFalse(AirspaceManifest.isSafeRelativePath("a/../b"))
        assertFalse(AirspaceManifest.isSafeRelativePath(""))
    }

    private fun expectInvalid(json: String) {
        try {
            AirspaceManifest.parse(json)
            fail("非法 manifest 应当被拒绝")
        } catch (e: AirspaceException) {
            assertEquals(AirspaceException.Kind.MANIFEST_INVALID, e.kind)
        }
    }
}
