package io.legado.app.ui.book.vectorize

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStream

class BertTokenizer(context: Context, modelPath: String = "models/bge-small-zh/tokenizer.json") {

    private val vocab: Map<String, Int>
    private val invVocab: Map<Int, String>
    private val unkTokenId: Int
    private val clsTokenId: Int
    private val sepTokenId: Int
    private val padTokenId: Int
    private val maxLen: Int

    init {
        val json = context.assets.open(modelPath).use { it.readBytes().toString(Charsets.UTF_8) }
        val root = Gson().fromJson(json, JsonObject::class.java)
        val model = root.getAsJsonObject("model")
        val vocabObj = model.getAsJsonObject("vocab")

        val mutableVocab = mutableMapOf<String, Int>()
        for (entry in vocabObj.entrySet()) {
            mutableVocab[entry.key] = entry.value.asInt
        }
        vocab = mutableVocab
        invVocab = mutableVocab.entries.associate { (k, v) -> v to k }

        val addedTokens = root.getAsJsonArray("added_tokens") ?: root.getAsJsonObject("added_tokens")
        unkTokenId = vocab.getOrDefault("[UNK]", 100)
        clsTokenId = vocab.getOrDefault("[CLS]", 101)
        sepTokenId = vocab.getOrDefault("[SEP]", 102)
        padTokenId = vocab.getOrDefault("[PAD]", 0)
        maxLen = 512
    }

    fun encode(text: String, maxLength: Int = maxLen): TokenizedOutput {
        val cleanText = normalize(text)
        val tokens = tokenize(cleanText)

        val tokenIds = mutableListOf<Int>()
        tokenIds.add(clsTokenId)

        for (token in tokens) {
            if (tokenIds.size >= maxLength - 1) break
            val subTokens = wordPiece(token)
            for (st in subTokens) {
                if (tokenIds.size >= maxLength - 1) break
                tokenIds.add(vocab.getOrDefault(st, unkTokenId))
            }
        }

        tokenIds.add(sepTokenId)

        val attentionMask = MutableList(tokenIds.size) { 1 }
        val tokenTypeIds = MutableList(tokenIds.size) { 0 }

        return TokenizedOutput(
            inputIds = tokenIds.toIntArray(),
            attentionMask = attentionMask.toIntArray(),
            tokenTypeIds = tokenTypeIds.toIntArray(),
            actualLength = tokenIds.size
        )
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            when {
                c == ' ' || c == '\t' || c == '\n' || c == '\r' -> {
                    if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                }
                c.code in 0x4E00..0x9FFF -> sb.append(c)
                c.code in 0x3400..0x4DBF -> sb.append(c)
                c.code in 0xFF00..0xFFEF -> sb.append((c.code - 0xFEE0).toChar())
                c.code in 0x0041..0x005A -> sb.append(c.lowercaseChar())
                c.isLetterOrDigit() -> sb.append(c)
                c in ",.!?;:\"'()[]<>\u3001\u3002\uff01\uff1f\uff1b\uff1a\u201c\u201d\u2018\u2019\uff08\uff09\u3010\u3011\u300a\u300b" -> sb.append(c)
                c in ",.!?;:\"'()[]<>" -> sb.append(c)
                else -> sb.append(c)
            }
        }
        return sb.toString().trim()
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()

        for (c in text) {
            if (c == ' ') {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
            } else if (c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF) {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                tokens.add(c.toString())
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())

        return tokens
    }

    private fun wordPiece(token: String): List<String> {
        if (token.length > 200) return listOf(token)

        val subTokens = mutableListOf<String>()
        var start = 0

        while (start < token.length) {
            var end = token.length
            var found = false

            while (start < end) {
                val substr = if (start == 0) token.substring(start, end) else "##${token.substring(start, end)}"
                if (vocab.containsKey(substr)) {
                    subTokens.add(substr)
                    start = end
                    found = true
                    break
                }
                end--
            }

            if (!found) {
                subTokens.add("[UNK]")
                break
            }
        }

        return subTokens
    }

    data class TokenizedOutput(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val tokenTypeIds: IntArray,
        val actualLength: Int
    )
}
