package com.chuhan.qiyuan.data

import android.content.Context
import org.json.JSONObject

/**
 * 设置存储：API 配置 + 人设。全部存应用私有目录，不上传。
 */
data class AppSettings(
    val endpoint: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val temperature: Double = 0.7,
    val timeoutSec: Long = 60,
    val moveMaxTokens: Int = 2500,
    val chatMaxTokens: Int = 600,
    val personaId: String = "friendly"
)

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("chuhan_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val raw = prefs.getString("json", null) ?: return AppSettings()
        return try {
            val o = JSONObject(raw)
            AppSettings(
                endpoint = o.optString("endpoint", "https://api.deepseek.com"),
                apiKey = o.optString("apiKey", ""),
                model = o.optString("model", "deepseek-chat"),
                temperature = o.optDouble("temperature", 0.7),
                timeoutSec = o.optLong("timeoutSec", 60),
                moveMaxTokens = o.optInt("moveMaxTokens", 2500).coerceAtLeast(2000),
                chatMaxTokens = o.optInt("chatMaxTokens", 600).coerceAtLeast(500),
                personaId = o.optString("personaId", "friendly")
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun save(s: AppSettings) {
        val o = JSONObject()
        o.put("endpoint", s.endpoint)
        o.put("apiKey", s.apiKey)
        o.put("model", s.model)
        o.put("temperature", s.temperature)
        o.put("timeoutSec", s.timeoutSec)
        o.put("moveMaxTokens", s.moveMaxTokens)
        o.put("chatMaxTokens", s.chatMaxTokens)
        o.put("personaId", s.personaId)
        prefs.edit().putString("json", o.toString()).apply()
    }
}