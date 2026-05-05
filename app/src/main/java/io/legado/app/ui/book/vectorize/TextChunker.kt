package io.legado.app.ui.book.vectorize

data class TextChunk(
    val text: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chunkIndex: Int
)

object TextChunker {

    private const val MAX_CHUNK_TOKENS = 300
    private const val OVERLAP_SENTENCES = 1

    fun chunkChapter(
        content: String,
        chapterIndex: Int,
        chapterTitle: String
    ): List<TextChunk> {
        if (content.isBlank()) return emptyList()

        val sentences = splitSentences(content)
        if (sentences.isEmpty()) return emptyList()

        val chunks = mutableListOf<TextChunk>()
        var chunkIndex = 0
        var i = 0

        while (i < sentences.size) {
            val chunkSentences = mutableListOf<String>()
            var tokenCount = 0

            var j = i
            while (j < sentences.size && tokenCount + estimateTokens(sentences[j]) <= MAX_CHUNK_TOKENS) {
                chunkSentences.add(sentences[j])
                tokenCount += estimateTokens(sentences[j])
                j++
            }

            if (chunkSentences.isEmpty()) {
                chunkSentences.add(sentences[i])
                j = i + 1
            }

            val chunkText = chunkSentences.joinToString("")
            chunks.add(TextChunk(
                text = chunkText,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                chunkIndex = chunkIndex
            ))
            chunkIndex++

            val overlap = if (j < sentences.size) OVERLAP_SENTENCES else 0
            i = j - overlap
            if (i <= chunks.lastIndex && j >= sentences.size) break
            if (i == j) i++
        }

        return chunks
    }

    private fun splitSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val sb = StringBuilder()

        for (c in text) {
            sb.append(c)
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '\n') {
                val s = sb.toString().trim()
                if (s.isNotEmpty()) sentences.add(s)
                sb.clear()
            }
        }
        val remaining = sb.toString().trim()
        if (remaining.isNotEmpty()) sentences.add(remaining)

        return sentences
    }

    private fun estimateTokens(text: String): Int {
        var count = 0
        for (c in text) {
            count += if (c.code > 127) 2 else 1
        }
        return count
    }
}
