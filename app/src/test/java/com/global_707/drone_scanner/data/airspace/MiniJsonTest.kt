package com.global_707.drone_scanner.data.airspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 极简 JSON 解析器单测：manifest 解析的正确性完全依赖它 */
class MiniJsonTest {

    @Test
    fun `解析嵌套对象与数组`() {
        val json = """
            {
              "a": 1,
              "b": [1, 2.5, "x"],
              "c": { "d": true, "e": null }
            }
        """.trimIndent()
        val root = MiniJson.parse(json) as Map<*, *>
        assertEquals(1.0, root["a"])
        val b = root["b"] as List<*>
        assertEquals(3, b.size)
        assertEquals(2.5, b[1])
        assertEquals("x", b[2])
        val c = root["c"] as Map<*, *>
        assertEquals(true, c["d"])
        assertNull(c["e"])
    }

    @Test
    fun `解析字符串转义`() {
        val json = """{"s":"a\"b\\c\/d\n\t\u4E2D"}"""
        val root = MiniJson.parse(json) as Map<*, *>
        assertEquals("a\"b\\c/d\n\t中", root["s"])
    }

    @Test
    fun `解析负数与科学计数法`() {
        val root = MiniJson.parse("""{"a":-12,"b":1.5e2}""") as Map<*, *>
        assertEquals(-12.0, root["a"])
        assertEquals(150.0, root["b"])
    }

    @Test
    fun `空对象与空数组`() {
        val root = MiniJson.parse("""{"a":{},"b":[]}""") as Map<*, *>
        assertTrue((root["a"] as Map<*, *>).isEmpty())
        assertTrue((root["b"] as List<*>).isEmpty())
    }

    @Test
    fun `多余内容报错`() {
        try {
            MiniJson.parse("""{"a":1} {"b":2}""")
            fail("应当抛出 JsonException")
        } catch (e: MiniJson.JsonException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun `字符串未闭合报错`() {
        try {
            MiniJson.parse("""{"a":"oops}""")
            fail("应当抛出 JsonException")
        } catch (e: MiniJson.JsonException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }
}
