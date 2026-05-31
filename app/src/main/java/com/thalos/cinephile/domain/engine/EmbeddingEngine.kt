package com.thalos.cinephile.domain.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import kotlin.math.sqrt

class EmbeddingEngine(context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val tokenizer: WordPieceTokenizer

    init {
        tokenizer = WordPieceTokenizer(loadVocab(context))
        try {
            val modelPath = copyAssetIfNeeded(context, "model.onnx")
            session = env.createSession(modelPath, OrtSession.SessionOptions())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAssetIfNeeded(context: Context, assetName: String): String {
        val modelFile = java.io.File(context.filesDir, assetName)
        if (modelFile.exists() && modelFile.length() > 0L) return modelFile.absolutePath

        context.assets.open(assetName).use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return modelFile.absolutePath
    }

    private fun loadVocab(context: Context): Map<String, Long> {
        return try {
            context.assets.open("vocab.txt").bufferedReader().useLines { lines ->
                lines.mapIndexed { index, token -> token to index.toLong() }.toMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val sessionLocal = session
        if (sessionLocal == null || tokenizer.isFallback) {
            return@withContext fallbackEmbedding(text)
        }

        try {
            val encoded = tokenizer.encode(text, maxLength = 128)
            val inputIds = encoded.inputIds
            val attentionMask = encoded.attentionMask
            val tokenTypeIds = LongArray(inputIds.size) { 0L }
            val shape = longArrayOf(1, inputIds.size.toLong())

            OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape).use { inputTensor ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape).use { maskTensor ->
                    OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape).use { typeTensor ->
                        val inputs = mutableMapOf<String, OnnxTensor>(
                            "input_ids" to inputTensor,
                            "attention_mask" to maskTensor
                        )
                        if (sessionLocal.inputNames.contains("token_type_ids")) {
                            inputs["token_type_ids"] = typeTensor
                        }

                        sessionLocal.run(inputs).use { outputs ->
                            val value = outputs[0].value
                            when (value) {
                                is Array<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val tokenEmbeddings = (value as Array<Array<FloatArray>>)[0]
                                    val pooled = meanPooling(tokenEmbeddings, attentionMask)
                                    normalize(pooled)
                                }
                                is FloatArray -> normalize(value)
                                else -> fallbackEmbedding(text)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackEmbedding(text)
        }
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray> = withContext(Dispatchers.Default) {
        val jobs = texts.map { text ->
            async { embed(text) }
        }
        jobs.awaitAll()
    }

    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val dim = tokenEmbeddings[0].size
        val result = FloatArray(dim) { 0f }
        var count = 0
        for (i in tokenEmbeddings.indices) {
            if (attentionMask.getOrNull(i) == 1L) {
                for (j in 0 until dim) {
                    result[j] += tokenEmbeddings[i][j]
                }
                count++
            }
        }
        if (count > 0) {
            for (j in 0 until dim) {
                result[j] /= count
            }
        }
        return result
    }

    fun normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { it * it.toDouble() }).toFloat()
        if (norm < 1e-8f) return vec
        return vec.map { it / norm }.toFloatArray()
    }

    private fun fallbackEmbedding(text: String): FloatArray {
        val dim = 384
        val vec = FloatArray(dim) { 0f }
        val words = text.split(Regex("\\W+")).filter { it.length > 2 }
        for (word in words) {
            val hash = word.hashCode()
            for (i in 0 until dim) {
                vec[i] += ((hash * (i + 1) * 31) % 1000) / 1000f
            }
        }
        return normalize(vec)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        val length = minOf(a.size, b.size)
        for (i in 0 until length) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = (sqrt(na.toDouble()) * sqrt(nb.toDouble())).toFloat()
        return if (denom < 1e-8f) 0f else dot / denom
    }

    fun parseEmbedding(str: String): FloatArray {
        if (str.isBlank()) return FloatArray(384) { 0f }
        return str.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
    }

    fun serializeEmbedding(vec: FloatArray): String {
        return vec.joinToString(",") { "%.6f".format(it) }
    }

    private data class EncodedInput(
        val inputIds: LongArray,
        val attentionMask: LongArray
    )

    private class WordPieceTokenizer(private val vocab: Map<String, Long>) {
        val isFallback: Boolean = vocab.isEmpty()

        private val clsId = vocab["[CLS]"] ?: 101L
        private val sepId = vocab["[SEP]"] ?: 102L
        private val unkId = vocab["[UNK]"] ?: 100L

        fun encode(text: String, maxLength: Int): EncodedInput {
            val pieces = mutableListOf<Long>()
            pieces += clsId
            basicTokenize(text).forEach { token ->
                pieces += wordPiece(token)
                if (pieces.size >= maxLength - 1) return@forEach
            }
            if (pieces.size > maxLength - 1) {
                pieces.subList(maxLength - 1, pieces.size).clear()
            }
            pieces += sepId
            val ids = pieces.toLongArray()
            val mask = LongArray(ids.size) { 1L }
            return EncodedInput(ids, mask)
        }

        private fun basicTokenize(text: String): List<String> {
            val normalized = text.lowercase()
            val out = mutableListOf<String>()
            val current = StringBuilder()
            normalized.forEach { ch ->
                when {
                    ch.isLetterOrDigit() -> current.append(ch)
                    ch.isWhitespace() -> flush(current, out)
                    else -> {
                        flush(current, out)
                        out += ch.toString()
                    }
                }
            }
            flush(current, out)
            return out.filter { it.isNotBlank() }
        }

        private fun flush(current: StringBuilder, out: MutableList<String>) {
            if (current.isNotEmpty()) {
                out += current.toString()
                current.clear()
            }
        }

        private fun wordPiece(token: String): List<Long> {
            if (token.length > 100) return listOf(unkId)
            val output = mutableListOf<Long>()
            var start = 0
            while (start < token.length) {
                var end = token.length
                var found: String? = null
                while (start < end) {
                    val sub = token.substring(start, end)
                    val piece = if (start == 0) sub else "##$sub"
                    if (vocab.containsKey(piece)) {
                        found = piece
                        break
                    }
                    end--
                }
                if (found == null) return listOf(unkId)
                output += vocab.getValue(found)
                start = end
            }
            return output
        }
    }
}
