package io.legado.app.ui.book.vectorize

data class TextChunk(
    val text: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chunkIndex: Int
)

object TextChunker {

    private const val MAX_CHUNK_TOKENS = 300

    fun chunkChapter(
        content: String,
        chapterIndex: Int,
        chapterTitle: String
    ): List<TextChunk> {
        if (content.isBlank()) return emptyList()

        val chunks = mutableListOf<TextChunk>()
        val buffer = StringBuilder()
        var bufferTokens = 0
        var chunkIndex = 0

        for (c in content) {
            buffer.append(c)
            bufferTokens += if (c.code > 127) 2 else 1

            val isEnd = c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '\n'

            if (isEnd && bufferTokens >= MAX_CHUNK_TOKENS) {
                val text = buffer.toString().trim()
                if (text.isNotEmpty()) {
                    chunks.add(TextChunk(text, chapterIndex, chapterTitle, chunkIndex++))
                }
                buffer.clear()
                bufferTokens = 0
            }
        }

        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) {
            chunks.add(TextChunk(remaining, chapterIndex, chapterTitle, chunkIndex))
        }

        return chunks
    }
}
