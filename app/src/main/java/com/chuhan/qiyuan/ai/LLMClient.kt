package com.chuhan.qiyuan.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容接口客户端（/chat/completions）。
 * 兼容 t80 中转、智谱官方、DeepSeek 官方等一切 OpenAI 风格端点。
 */
class LLMClient(
    private var endpoint: String,
    private var apiKey: String,
    private var model: String,
    private var temperature: Double,
    private var timeoutSec: Long
) {
    private var client = OkHttpClient.Builder()
        .connectTimeout(timeoutSec, TimeUnit.SECONDS)
        .readTimeout(timeoutSec, TimeUnit.SECONDS)
        .writeTimeout(timeoutSec, TimeUnit.SECONDS)
        .build()

    fun update(endpoint: String, apiKey: String, model: String, temperature: Double, timeoutSec: Long) {
        this.endpoint = endpoint
        this.apiKey = apiKey
        this.model = model
        this.temperature = temperature
        client = OkHttpClient.Builder()
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .writeTimeout(timeoutSec, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 发送对话，返回模型纯文本回复。
     * maxTokens：走子建议 700+，聊天可设小（150~300）以提速。
     */
    @Throws(Exception::class)
    suspend fun chat(messages: List<Pair<String, String>>, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val arr = buildString {
                append('[')
                messages.forEachIndexed { i, (role, content) ->
                    if (i > 0) append(',')
                    append("""{"role":"${role}","content":""")
                    append(kotlinx.serialization.json.Json.encodeToString(
                        kotlinx.serialization.serializer<String>(), content))
                    append('}')
                }
                append(']')
            }
            val bodyJson = """{"model":"$model","messages":$arr,"temperature":$temperature,"max_tokens":$maxTokens}"""
            val url = if (endpoint.endsWith("/")) endpoint + "chat/completions"
                      else endpoint + "/chat/completions"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw RuntimeException("HTTP ${resp.code}: ${text.take(300)}")
                    }
                    extractContent(text) ?: throw RuntimeException("无法从响应中提取回复：${text.take(300)}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException(e.message ?: "网络请求失败", e)
            }
        }

    private fun extractContent(respText: String): String? {
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            val root = json.parseToJsonElement(respText) as? kotlinx.serialization.json.JsonObject ?: return null
            val choices = root["choices"] as? kotlinx.serialization.json.JsonArray ?: return null
            val first = choices.firstOrNull() as? kotlinx.serialization.json.JsonObject ?: return null
            val msg = first["message"] as? kotlinx.serialization.json.JsonObject ?: return null
            (msg["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        } catch (e: Exception) {
            null
        }
    }

    /** 快速连通性测试：返回 null 表示成功，否则返回错误描述 */
    suspend fun testConnection(): String? = try {
        val reply = chat(listOf("user" to "请只回复两个字：正常"), 16)
        if (reply.isBlank()) "模型回复为空" else null
    } catch (e: Exception) {
        e.message ?: "连接失败"
    }
}