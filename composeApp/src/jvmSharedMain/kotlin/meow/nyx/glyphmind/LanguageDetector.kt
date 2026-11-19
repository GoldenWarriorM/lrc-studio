// SPDX-License-Identifier: GPL-3.0-only
package meow.nyx.glyphmind

import kotlin.math.exp
import kotlin.math.sqrt

/** Confidence is a score margin, not a calibrated probability. */
data class Detection(
    val language: String,
    val confidence: Float,
)

object LanguageDetector {
    const val CONFIDENCE_THRESHOLD: Float = 0.55f
    private val model by lazy { Model(detectorData) }

    fun detect(text: String): Detection {
        val counts = text.filter(Char::isLetter).groupingBy(::script).eachCount()
        if (counts.values.sum() < 2) return Detection("unknown", 0f)
        val dominant = counts.maxBy { it.value }
        if ((counts["Kana"] ?: 0) > 0 && (counts["Kana"] ?: 0) + (counts["Han"] ?: 0) >= dominant.value) return Detection("ja", 0.99f)
        val candidates = model.groups.indices.filter { model.groups[it] == dominant.key }
        if (candidates.isEmpty()) return Detection("unknown", 0f)
        if (candidates.size == 1) return Detection(model.labels[candidates.single()], 0.99f)
        val features = features(text)
        val scores =
            candidates
                .map { index ->
                    var score = 0L
                    for (feature in features) score += model.weights[index * model.width + feature]
                    index to score
                }.sortedByDescending { it.second }
        val margin = (scores[0].second - scores[1].second).toDouble() / 8 / sqrt(features.size.toDouble())
        val confidence = (1 - exp(-margin / 0.25)).toFloat()
        return Detection(if (confidence >= CONFIDENCE_THRESHOLD) model.labels[scores.first().first] else "unknown", confidence)
    }

    fun detectLines(lines: List<String>): List<Detection> = lines.map(::detect)

    /** Unknown letters retain their share; punctuation and instrumental lines have zero weight. */
    fun languageShare(lines: List<String>): Map<String, Float> {
        val counts = mutableMapOf<String, Long>()
        for (line in lines) {
            val letters = line.count(Char::isLetter).toLong()
            if (letters > 0) {
                val language = detect(line).language
                counts[language] = (counts[language] ?: 0L) + letters
            }
        }
        val total = counts.values.sum()
        return if (total == 0L) emptyMap() else counts.mapValues { it.value.toFloat() / total }
    }

    /** Checks script runs and overlapping three-word passages for mixed-language vocals. */
    fun hasForeignPassage(
        text: String,
        targetLanguage: String,
    ): Boolean {
        val target = canonicalLanguage(targetLanguage)
        if (target.isBlank() || target == "unknown") return false

        fun foreign(
            passage: String,
            threshold: Float = CONFIDENCE_THRESHOLD,
        ): Boolean {
            if (passage.count(Char::isLetter) < 3) return false
            val result = detect(passage)
            return result.language != "unknown" && result.confidence >= threshold && canonicalLanguage(result.language) != target
        }
        if (foreign(text)) return true
        val runs = mutableListOf<String>()
        var family: String? = null
        val run = StringBuilder()
        for (char in text) {
            val next = if (char.isLetter()) script(char).let { if (it == "Kana") "Han" else it } else family
            if (family != null && next != family) {
                runs.add(run.toString())
                run.clear()
            }
            run.append(char)
            family = next
        }
        runs.add(run.toString())
        if (runs.any { foreign(it) }) return true
        val words = text.split(Regex("\\s+|[,;.!?，。！？]")).filter { it.any(Char::isLetter) }
        // Short windows need stronger evidence than whole lines or distinct script runs.
        return words.windowed(3).any { foreign(it.joinToString(" "), threshold = 0.95f) }
    }

    private fun canonicalLanguage(code: String): String =
        when (val base = code.lowercase().replace('_', '-').substringBefore('-')) {
            "iw" -> "he"
            "in" -> "id"
            "tl" -> "fil"
            "no" -> "nb"
            else -> base
        }

    private fun features(text: String): IntArray {
        val normalized =
            buildString {
                append(' ')
                for (char in text) {
                    if (char.isLetter() ||
                        char.category == CharCategory.NON_SPACING_MARK ||
                        char.category == CharCategory.COMBINING_SPACING_MARK ||
                        char.category == CharCategory.ENCLOSING_MARK
                    ) {
                        append(char.lowercaseChar())
                    } else if (last() != ' ') {
                        append(' ')
                    }
                }
                if (last() != ' ') append(' ')
            }
        val result = ArrayList<Int>(normalized.length * 5)
        for (i in normalized.indices) {
            var hash = 2166136261u.toInt()
            for (j in i until minOf(i + 5, normalized.length)) {
                hash = (hash xor normalized[j].code) * 16777619
                result.add((hash.toUInt() % model.width.toUInt()).toInt())
            }
        }
        return result.toIntArray()
    }

    private class Model(
        data: ByteArray,
    ) {
        val labels: Array<String>
        val groups: Array<String>
        val width: Int
        val weights: ByteArray

        init {
            val input = BinaryInput(data)
            require(input.readInt() == 0x474c4431)
            val count = input.readUnsignedShort()
            width = input.readUnsignedShort()
            labels = Array(count) { "" }
            groups = Array(count) { "" }
            repeat(count) {
                labels[it] = input.readUTF()
                groups[it] = input.readUTF()
            }
            weights = data.copyOfRange(input.position, data.size)
            require(weights.size == count * width)
        }
    }

    private fun script(char: Char): String =
        when (char.code) {
            in 0x3040..0x30ff -> "Kana"
            in 0x3400..0x9fff, in 0xf900..0xfaff -> "Han"
            in 0xac00..0xd7a3, in 0x1100..0x11ff -> "Hang"
            in 0x0400..0x052f -> "Cyrl"
            in 0x0600..0x06ff, in 0x0750..0x077f, in 0x08a0..0x08ff -> "Arab"
            in 0x0900..0x097f -> "Deva"
            in 0x0980..0x09ff -> "Beng"
            in 0x0a00..0x0a7f -> "Guru"
            in 0x0b80..0x0bff -> "Taml"
            in 0x0c00..0x0c7f -> "Telu"
            in 0x0d00..0x0d7f -> "Mlym"
            in 0x0d80..0x0dff -> "Sinh"
            in 0x0e00..0x0e7f -> "Thai"
            in 0x0370..0x03ff -> "Grek"
            in 0x0590..0x05ff -> "Hebr"
            in 0x0000..0x024f, in 0x1e00..0x1eff -> "Latn"
            else -> "unknown"
        }
}
