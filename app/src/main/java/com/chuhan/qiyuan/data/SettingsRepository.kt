package com.chuhan.qiyuan.data

import android.content.Context
import org.json.JSONObject

/**
 * 设置存储：API 配置 + 人设。全部存应用私有目录，不上传。
 */
data class AppSettings(
    val endpoint: String = "https://api.t80nb.me/v1",
    val apiKey: String = "",
    val model: String = "glm-5.3-flash",
    val temperature: Double = 0.7,
    val timeoutSec: Long = 60,
    val moveMaxTokens: Int = 700,
    val chatMaxTokens: Int = 300,
    val personaId: String = "friendly"
)

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("chuhan_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val raw = prefs.getString("json", null) ?: return AppSettings()
        return try {
            val o = JSONObject(raw)
            AppSettings(
                endpoint = o.optString("endpoint", AppSettings().endpoint),
                apiKey = o.optString("apiKey", ""),
                model = o.optString("model", AppSettings().model),
                temperature = o.optDouble("temperature", 0.7),
                timeoutSec = o.optLong("timeoutSec", 60),
                moveMaxTokens = o.optInt("moveMaxTokens", 700),
                chatMaxTokens = o.optInt("chatMaxTokens", 300),
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