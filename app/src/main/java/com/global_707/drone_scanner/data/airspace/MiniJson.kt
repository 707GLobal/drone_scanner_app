package com.global_707.drone_scanner.data.airspace

/**
 * 极简 JSON 解析器（只覆盖 manifest 需要的子集：对象 / 数组 / 字符串 / 数字 / 布尔 / null）。
 *
 * 为什么不用 org.json：`org.json` 是 Android 框架类，在 JVM 单测里是抛异常的桩，
 * 用它会让 manifest 校验无法单测；引第三方库又违反"不加依赖"的体积红线。
 * 这里手写一个 ~150 行的严格解析器，纯 Kotlin，Android 与 JVM 单测行为一致。
 *
 * 解析结果类型：
 *  - 对象 → Map<String, Any?>
 *  - 数组 → List<Any?>
 *  - 字符串 → String；数字 → Double；布尔 → Boolean；null → null
 */
object MiniJson {

    class JsonException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("第 ${parser.pos} 个字符后存在多余内容")
        return value
    }

    private class Parser(private val src: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw JsonException("内容意外结束")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> if (c == '-' || c in '0'..'9') parseNumber()
                else throw JsonException("第 $pos 个字符非法：'$c'")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { pos++; return result }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return result }
                    else -> throw JsonException("对象缺少 ',' 或 '}'（第 $pos 个字符）")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { pos++; return result }
            while (true) {
                result.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return result }
                    else -> throw JsonException("数组缺少 ',' 或 ']'（第 $pos 个字符）")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("字符串未闭合")
                when (val c = src[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("转义序列未闭合")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw JsonException("\\u 转义长度不足")
                                val hex = src.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonException("\\u 转义非法：$hex")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("未知转义：\\$esc")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && (src[pos].isDigit() || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' || src[pos] == '+' || src[pos] == '-')) {
                pos++
            }
            val raw = src.substring(start, pos)
            return raw.toDoubleOrNull() ?: throw JsonException("非法数字：$raw")
        }

        private fun parseLiteral(text: String, value: Any?): Any? {
            if (!src.startsWith(text, pos)) throw JsonException("非法字面量（第 $pos 个字符）")
            pos += text.length
            return value
        }

        private fun peek(): Char {
            if (atEnd()) throw JsonException("内容意外结束")
            return src[pos]
        }

        private fun expect(c: Char) {
            if (atEnd() || src[pos] != c) throw JsonException("第 $pos 个字符处应为 '$c'")
            pos++
        }
    }
}
