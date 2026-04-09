package com.buddyapp.Buddy.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class KeyUsageStats(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val lastUsedTimestamp: Long = 0L
) {
    val errorRate: Float
        get() = if (totalRequests > 0) (failedRequests.toFloat() / totalRequests) * 100f else 0f
}

class UsageManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("api_usage_prefs", Context.MODE_PRIVATE)

    fun recordRequest(key: String, success: Boolean) {
        val stats = getStats(key)
        val newTotal = stats.totalRequests + 1
        val newSuccess = if (success) stats.successfulRequests + 1 else stats.successfulRequests
        val newFailed = if (!success) stats.failedRequests + 1 else stats.failedRequests

        val obj = JSONObject()
        obj.put("total", newTotal)
        obj.put("success", newSuccess)
        obj.put("failed", newFailed)
        obj.put("lastUsed", System.currentTimeMillis())

        prefs.edit().putString(key, obj.toString()).apply()
    }

    fun getStats(key: String): KeyUsageStats {
        val jsonStr = prefs.getString(key, null) ?: return KeyUsageStats()
        return try {
            val obj = JSONObject(jsonStr)
            KeyUsageStats(
                totalRequests = obj.optInt("total", 0),
                successfulRequests = obj.optInt("success", 0),
                failedRequests = obj.optInt("failed", 0),
                lastUsedTimestamp = obj.optLong("lastUsed", 0L)
            )
        } catch (e: Exception) {
            KeyUsageStats()
        }
    }

    fun deleteStats(key: String) {
        prefs.edit().remove(key).apply()
    }
}
