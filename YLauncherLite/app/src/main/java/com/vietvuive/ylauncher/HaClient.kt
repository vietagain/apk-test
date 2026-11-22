package com.vietvuive.ylauncher

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JSONObject
)

object HaClient {

    fun fetchAllStates(baseUrl: String, token: String): Map<String, EntityState>? {
        if (baseUrl.isBlank() || token.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$baseUrl/api/states")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(text)
                val map = HashMap<String, EntityState>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getString("entity_id")
                    val st = obj.optString("state", "unknown")
                    val attr = obj.optJSONObject("attributes") ?: JSONObject()
                    map[id] = EntityState(id, st, attr)
                }
                map
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun callService(baseUrl: String, token: String, entityId: String, action: String): Boolean {
        if (baseUrl.isBlank() || token.isBlank() || entityId.isBlank()) return false
        val domain = entityId.substringBefore(".", "homeassistant")
        val service = when (action) {
            "toggle" -> "toggle"
            "open" -> "open_cover"
            "close" -> "close_cover"
            "stop" -> "stop_cover"
            else -> action
        }
        val targetDomain = if (action in listOf("open", "close", "stop")) "cover" else domain

        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$baseUrl/api/services/$targetDomain/$service")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = JSONObject().put("entity_id", entityId).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
