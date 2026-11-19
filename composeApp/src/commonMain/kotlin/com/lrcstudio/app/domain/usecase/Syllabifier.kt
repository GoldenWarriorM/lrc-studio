package com.lrcstudio.app.domain.usecase

/**
 * Heuristic syllable splitter for English and Russian lyric words.
 *
 * Neither language has a purely letter-based syllabification rule (English is
 * phonetic, Russian is close to the school hyphenation rules), so this is an
 * approximation meant to give a usable starting point that the user can retime.
 */
object Syllabifier {

    private const val RU_VOWELS = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
    private const val EN_VOWELS = "aeiouyAEIOUY"

    private val EN_ONSETS = setOf(
        "bl", "br", "ch", "cl", "cr", "dr", "dw", "fl", "fr", "gl", "gr",
        "ph", "pl", "pr", "qu", "sc", "sh", "sk", "sl", "sm", "sn", "sp",
        "st", "sw", "th", "tr", "tw", "wh", "wr", "scr", "shr", "spl", "spr",
        "squ", "str", "thr", "sch"
    )

    private val EN_SUFFIXES = listOf(
        "ings", "ing", "ers", "ed", "er", "est", "ly", "tion", "sion", "ment",
        "ness", "ful", "less", "ous", "al", "en", "y", "s"
    )

    /** Splits one word into syllables; returns the word itself when unsure. */
    fun split(word: String): List<String> {
        if (word.length < 3) return listOf(word)
        val letters = word.filter { it.isLetter() }
        if (letters.length < 3) return listOf(word)
        return when {
            letters.all { it.isCyrillicLetter() } -> splitRussian(word)
            letters.all { it.isLatinLetter() } -> splitEnglish(word)
            else -> listOf(word)
        }
    }

    private fun splitRussian(word: String): List<String> {
        val vowels = word.indices.filter { word[it] in RU_VOWELS }
        if (vowels.size < 2) return listOf(word)
        val cuts = mutableListOf<Int>()
        for (i in 0 until vowels.size - 1) {
            val from = vowels[i]
            val to = vowels[i + 1]
            val cluster = word.substring(from + 1, to)
            // ь, ъ and й stay with the preceding vowel (мель-ни-ца, строй-ка).
            val softIndex = cluster.indexOfFirst { it == 'ь' || it == 'ъ' || it == 'й' || it == 'Ь' || it == 'Ъ' || it == 'Й' }
            val cut = when {
                cluster.isEmpty() -> to
                softIndex >= 0 -> from + 2 + softIndex
                cluster.length == 1 -> to - 1
                else -> from + 2
            }
            if (cut in 1 until word.length) cuts.add(cut)
        }
        return cutAt(word, cuts)
    }

    private fun splitEnglish(word: String): List<String> {
        val groups = mutableListOf<IntRange>()
        var start = -1
        for (i in word.indices) {
            val isVowel = word[i] in EN_VOWELS
            if (isVowel && start < 0) start = i
            if (!isVowel && start >= 0) {
                groups.add(start..(i - 1))
                start = -1
            }
        }
        if (start >= 0) groups.add(start..(word.lastIndex))
        if (groups.size < 2) return listOf(word)

        val cuts = mutableListOf<Int>()
        for (i in 0 until groups.size - 1) {
            val clusterStart = groups[i].last + 1
            val clusterEnd = groups[i + 1].first
            if (clusterEnd <= clusterStart) {
                // Vowel directly after vowel: split between them.
                cuts.add(clusterEnd)
                continue
            }
            val cluster = word.substring(clusterStart, clusterEnd)
            val rest = word.substring(clusterEnd)
            val cut = when {
                // Doubled consonant (bet-ter) splits between the twins.
                cluster.length >= 2 &&
                    cluster[cluster.length - 1].lowercaseChar() ==
                    cluster[cluster.length - 2].lowercaseChar() -> clusterStart + cluster.length - 1
                // Common suffix after a cluster (melt-ing) stays in one piece.
                cluster.length >= 2 && EN_SUFFIXES.any { rest.startsWith(it, ignoreCase = true) } ->
                    clusterEnd
                else -> clusterStart + maxOnsetStart(cluster)
            }
            if (cut in 1 until word.length) cuts.add(cut)
        }
        return cutAt(word, cuts)
    }

    /** How much of the cluster may start the next syllable (maximal onset). */
    private fun maxOnsetStart(cluster: String): Int {
        val max = minOf(3, cluster.length)
        for (len in max downTo 1) {
            if (len == 1) return cluster.length - 1
            if (EN_ONSETS.contains(cluster.takeLast(len).lowercase())) return cluster.length - len
        }
        return cluster.length - 1
    }

    private fun cutAt(word: String, cuts: List<Int>): List<String> {
        if (cuts.isEmpty()) return listOf(word)
        val result = mutableListOf<String>()
        var from = 0
        for (cut in cuts.distinct().sorted()) {
            if (cut <= from || cut >= word.length) continue
            result.add(word.substring(from, cut))
            from = cut
        }
        result.add(word.substring(from))
        return result.filter { it.isNotEmpty() }
    }
}

private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isCyrillicLetter(): Boolean = this in 'а'..'я' || this in 'А'..'Я' || this == 'ё' || this == 'Ё'
