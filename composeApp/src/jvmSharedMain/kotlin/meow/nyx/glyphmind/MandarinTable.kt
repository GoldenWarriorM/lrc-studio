// SPDX-License-Identifier: GPL-3.0-only
// Metrolist Project (C) 2026, contributors including l6t9. See NOTICE.md.
package meow.nyx.glyphmind

internal fun MandarinTable.romanize(text: String): String {
    val tokens = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char <= ' ') {
            index++
            continue
        }
        var end = index + 1
        if (char in '\u4e00'..'\u9fa5') {
            var phrase: String? = null
            for (length in minOf(this.maxPhraseLength, text.length - index) downTo 2) {
                phrase = this.phrases[text.substring(index, index + length)]
                if (phrase != null) {
                    end = index + length
                    break
                }
            }
            tokens += phrase ?: this.characters[char.code] ?: char.toString()
        } else {
            if (char.isPinyinWordChar()) {
                while (end < text.length && text[end] !in '\u4e00'..'\u9fa5' && text[end].isPinyinWordChar()) end++
            }
            val token = text.substring(index, end)
            tokens +=
                if (token.any { it.code > 127 }) {
                    token.map { this.characters[it.code] ?: it.toString() }.joinToString(" ")
                } else {
                    token
                }
        }
        index = end
    }
    return tokens
        .joinToString(" ")
        .replace(Regex(" {2,}"), " ")
        .replace(Regex("\\s+([，。！？,.!?;:])"), "$1")
        .replace(Regex("([，。！？,.!?;:])\\s+"), "$1")
}

private fun Char.isPinyinWordChar() = isDigit() || isLowerCase() || isUpperCase() || this == '\''

internal class MandarinTable(
    data: ByteArray,
) {
    val characters = arrayOfNulls<String>(65536)
    val phrases = HashMap<String, String>()
    val maxPhraseLength: Int

    init {
        BinaryInput(data).let { input ->
            check(input.readInt() == 0x4d505931)
            val syllables = Array(input.readUnsignedShort()) { input.readUTF() }
            val alphabet = CharArray(input.readUnsignedShort()) { input.readChar() }
            val syllableWidth = input.readUnsignedByte()
            val charWidth = input.readUnsignedByte()
            val runs = input.readUnsignedShort()
            val phraseCount = input.readInt()
            val bits = PinyinBits(input)
            repeat(runs) {
                val start = bits.read(16)
                repeat(bits.read(16)) { offset -> characters[start + offset] = syllables[bits.read(syllableWidth)] }
            }
            var previous = ""
            repeat(phraseCount) {
                val prefix = bits.read(4)
                val suffix = bits.read(4)
                val phrase = previous.take(prefix) + CharArray(suffix) { alphabet[bits.read(charWidth)] }.concatToString()
                phrases[phrase] =
                    phrase
                        .map { char ->
                            if (bits.read(1) == 1) syllables[bits.read(syllableWidth)] else characters[char.code] ?: char.toString()
                        }.joinToString(" ")
                previous = phrase
            }
        }
        maxPhraseLength = phrases.keys.maxOf(String::length)
    }
}

private class PinyinBits(
    private val input: BinaryInput,
) {
    private var value = 0
    private var length = 0

    fun read(width: Int): Int {
        while (length < width) {
            value = (value shl 8) or input.readUnsignedByte()
            length += 8
        }
        length -= width
        val result = value ushr length
        value = value and ((1 shl length) - 1)
        return result
    }
}
