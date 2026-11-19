// SPDX-License-Identifier: GPL-3.0-only
package meow.nyx.glyphmind

internal class BinaryInput(
    private val bytes: ByteArray,
) {
    var position = 0
        private set

    fun readUnsignedByte(): Int = bytes[position++].toInt() and 255

    fun readUnsignedShort(): Int = (readUnsignedByte() shl 8) or readUnsignedByte()

    fun readShort(): Short = readUnsignedShort().toShort()

    fun readInt(): Int = (readUnsignedShort() shl 16) or readUnsignedShort()

    fun readChar(): Char = readUnsignedShort().toChar()

    fun readUTF(): String {
        val length = readUnsignedShort()
        require(length <= bytes.size - position)
        return bytes.decodeToString(position, position + length, throwOnInvalidSequence = true).also { position += length }
    }

    fun requireEnd() = require(position == bytes.size) { "Trailing pack data" }
}
