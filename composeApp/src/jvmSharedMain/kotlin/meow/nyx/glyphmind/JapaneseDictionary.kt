/*
 * Copyright 2010-2015 Atilika Inc. and contributors. Apache-2.0.
 * Port of Kuromoji 0.9.0 ViterbiBuilder and ViterbiSearcher, NORMAL mode.
 * Uses sorted prefix lookup and GJP1 records instead of the Java double-array trie.
 * See licenses/Kuromoji-LICENSE.md and licenses/Kuromoji-CONTRIBUTORS.md.
 */
package meow.nyx.glyphmind

internal class JapaneseDictionary(
    data: ByteArray,
) {
    internal data class Token(
        val surface: String,
        val reading: String,
    )

    private data class Entry(
        val left: Int,
        val right: Int,
        val cost: Int,
        val reading: String,
    )

    private data class Category(
        val invoke: Boolean,
        val group: Boolean,
        val entries: List<Entry>,
    )

    private class Node(
        val start: Int,
        val end: Int,
        val entry: Entry,
        val total: Long,
        val previous: Node?,
    )

    private val surfaces: MutableList<String>
    private val entries: Array<List<Entry>>
    private val categories: Array<Category>
    private val characterCategories: Array<IntArray>
    private val costs: ShortArray
    private val costSize: Int
    private val maxLength: Int

    init {
        val input = BinaryInput(data)
        require(input.readInt() == 0x474a5031)
        val readings = Array(input.readInt()) { input.readUTF() }
        costSize = input.readUnsignedShort()
        costs = ShortArray(costSize * costSize) { input.readShort() }

        fun entry() = Entry(input.readUnsignedShort(), input.readUnsignedShort(), input.readShort().toInt(), readings[input.readInt()])
        categories =
            Array(input.readUnsignedByte()) {
                Category(input.readUnsignedByte() != 0, input.readUnsignedByte() != 0, List(input.readUnsignedShort()) { entry() })
            }
        characterCategories = Array(65536) { intArrayOf() }
        var start = 0
        repeat(input.readInt()) {
            val length = input.readInt()
            val cats = IntArray(input.readUnsignedByte()) { input.readUnsignedByte() }
            repeat(length) { characterCategories[start++] = cats }
        }
        require(start == 65536)
        val count = input.readInt()
        surfaces = MutableList(count) { "" }
        entries = Array(count) { emptyList() }
        var previous = ""
        repeat(count) { i ->
            val word = previous.take(input.readUnsignedShort()) + input.readUTF()
            require(i == 0 || previous < word)
            surfaces[i] = word
            entries[i] = List(input.readUnsignedShort()) { entry() }
            previous = word
        }
        maxLength = surfaces.maxOf(String::length)
        input.requireEnd()
    }

    fun tokenize(text: String): List<Token> =
        buildList {
            var start = 0
            text.forEachIndexed { i, c ->
                if (c == '。' || c == '、') {
                    addAll(tokenizePart(text.substring(start, i + 1)))
                    start = i + 1
                }
            }
            if (start < text.length) addAll(tokenizePart(text.substring(start)))
        }

    private fun tokenizePart(text: String): List<Token> {
        val ends = Array(text.length + 1) { mutableListOf<Node>() }
        val boundary = Entry(0, 0, 0, "")
        ends[0].add(Node(0, 0, boundary, 0, null))

        fun node(
            start: Int,
            end: Int,
            entry: Entry,
        ): Node {
            var best: Node? = null
            var cost = Long.MAX_VALUE
            for (left in ends[start]) {
                val candidate = left.total + costs[left.entry.right * costSize + entry.left] + entry.cost
                if (candidate < cost) {
                    cost = candidate
                    best = left
                }
            }
            return Node(start, end, entry, cost, best)
        }
        var unknownEnd = -1
        for (start in text.indices) {
            if (ends[start].isEmpty()) continue
            var found = false
            for (length in 1..minOf(maxLength, text.length - start)) {
                val prefix = text.substring(start, start + length)
                val index = surfaces.binarySearch(prefix)
                if (index >= 0) {
                    found = true
                    for (entry in entries[index]) ends[start + length].add(node(start, start + length, entry))
                } else {
                    val insertion = -index - 1
                    if (insertion == surfaces.size || !surfaces[insertion].startsWith(prefix)) break
                }
            }
            if (unknownEnd <= start) {
                characterCategories[text[start].code].forEachIndexed { i, category ->
                    val definition = categories[category]
                    if (definition.invoke || !found) {
                        var end = start + 1
                        if (definition.group) {
                            while (end < text.length && characterCategories[text[end].code].getOrNull(i) == category) end++
                        }
                        for (entry in definition.entries) ends[end].add(node(start, end, entry))
                        unknownEnd = end
                    }
                }
            }
        }
        var last = node(text.length, text.length, boundary).previous
        val result = mutableListOf<Token>()
        while (last != null && last.previous != null) {
            result.add(Token(text.substring(last.start, last.end), last.entry.reading))
            last = last.previous
        }
        result.reverse()
        return result
    }
}
