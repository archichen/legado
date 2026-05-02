package io.legado.app.data.entities

import java.io.File

/**
 * TTS 聚合段落数据类
 * 用于将多个短段落合并为一个 TTS 请求
 */
data class TTSSegment(
    val startIndex: Int,
    val endIndex: Int,
    val text: String,
    val paragraphLengths: List<Int>,
    var audioCached: Boolean = false,
    var audioFile: File? = null
) {
    val paragraphCount: Int get() = endIndex - startIndex + 1

    fun containsParagraph(paragraphIndex: Int): Boolean {
        return paragraphIndex in startIndex..endIndex
    }

    fun getParagraphOffset(paragraphIndex: Int): Int {
        if (paragraphIndex < startIndex || paragraphIndex > endIndex) return -1
        var offset = 0
        for (i in startIndex until paragraphIndex) {
            offset += paragraphLengths[i - startIndex]
        }
        return offset
    }

    fun getParagraphLength(paragraphIndex: Int): Int {
        if (paragraphIndex < startIndex || paragraphIndex > endIndex) return 0
        return paragraphLengths[paragraphIndex - startIndex]
    }

    companion object {
        /**
         * 将段落列表聚合为多个段落组
         * @param paragraphs 原始段落列表
         * @param maxLength 每个聚合段落的最大字符数
         * @return 聚合后的段落组列表
         */
        fun aggregate(paragraphs: List<String>, maxLength: Int = 100): List<TTSSegment> {
            if (paragraphs.isEmpty()) return emptyList()

            val segments = mutableListOf<TTSSegment>()
            var currentStart = 0
            var currentText = StringBuilder()
            var currentLengths = mutableListOf<Int>()

            for (i in paragraphs.indices) {
                val para = paragraphs[i]
                val newLength = currentText.length + para.length + if (currentText.isNotEmpty()) 1 else 0

                if (newLength > maxLength && currentText.isNotEmpty()) {
                    segments.add(
                        TTSSegment(
                            startIndex = currentStart,
                            endIndex = i - 1,
                            text = currentText.toString().trim(),
                            paragraphLengths = currentLengths.toList()
                        )
                    )
                    currentStart = i
                    currentText = StringBuilder()
                    currentLengths = mutableListOf()
                }

                if (currentText.isNotEmpty()) {
                    currentText.append("\n")
                }
                currentText.append(para)
                currentLengths.add(para.length + 1)
            }

            if (currentText.isNotEmpty()) {
                segments.add(
                    TTSSegment(
                        startIndex = currentStart,
                        endIndex = paragraphs.lastIndex,
                        text = currentText.toString().trim(),
                        paragraphLengths = currentLengths.toList()
                    )
                )
            }

            return segments
        }

        /**
         * 查找包含指定段落索引的聚合段落
         */
        fun findSegment(segments: List<TTSSegment>, paragraphIndex: Int): Int {
            return segments.indexOfFirst { it.containsParagraph(paragraphIndex) }
        }
    }
}
