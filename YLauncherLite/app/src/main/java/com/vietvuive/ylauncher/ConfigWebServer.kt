package com.vietvuive.ylauncher

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class ConfigWebServer(
    private val context: Context,
    port: Int = 8080,
    private val onConfigUpdated: () -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            uri == "/api/config" && method == Method.GET -> {
                val json = ConfigManager.getRawJson(context)
                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)
            }
            uri == "/api/config" && method == Method.POST -> {
                try {
                    val body = readUtf8Body(session)
                    val ok = ConfigManager.saveRawJson(context, body)
                    if (ok) {
                        onConfigUpdated()
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", "{\"status\":\"ok\"}")
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json; charset=utf-8", "{\"status\":\"invalid_json\"}")
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", "{\"status\":\"error\"}")
                }
            }
            uri == "/api/ha_entities" && method == Method.GET -> {
                try {
                    val cfg = ConfigManager.loadConfig(context)
                    val states = HaClient.fetchAllStates(cfg.haUrl, cfg.haToken)
                    if (states == null) {
                        newFixedLengthResponse(Response.Status.BAD_GATEWAY, "application/json; charset=utf-8", "{\"error\":\"Không kết nối được HA. Hãy kiểm tra URL/Token và bấm Lưu trước.\"}")
                    } else {
                        val arr = JSONArray()
                        for (st in states.values.sortedBy { it.entityId }) {
                            val friendly = st.attributes.optString("friendly_name", st.entityId)
                            val unit = st.attributes.optString("unit_of_measurement", "")
                            arr.put(JSONObject().apply {
                                put("id", st.entityId)
                                put("name", friendly)
                                put("state", st.state)
                                put("unit", unit)
                            })
                        }
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", arr.toString())
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", "{\"error\":\"Lỗi lấy danh sách từ HA\"}")
                }
            }
            uri == "/api/wallpaper" && method == Method.POST -> {
                try {
                    val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
                    val wpFile = File(context.filesDir, "wallpaper.jpg")
                    FileOutputStream(wpFile).use { out ->
                        val buf = ByteArray(8192)
                        var remaining = contentLength
                        while (remaining > 0) {
                            val toRead = minOf(buf.size, remaining)
                            val read = session.inputStream.read(buf, 0, toRead)
                            if (read == -1) break
                            out.write(buf, 0, read)
                            remaining -= read
                        }
                    }
                    onConfigUpdated()
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", "{\"status\":\"ok\"}")
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", "{\"status\":\"error\"}")
                }
            }
            uri == "/api/wallpaper" && method == Method.DELETE -> {
                val wpFile = File(context.filesDir, "wallpaper.jpg")
                if (wpFile.exists()) wpFile.delete()
                onConfigUpdated()
                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", "{\"status\":\"deleted\"}")
            }
            else -> {
                val html = try {
                    context.assets.open("index.html").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                } catch (e: Exception) {
                    "<h1>Lỗi không tìm thấy giao diện cấu hình</h1>"
                }
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            }
        }
    }

    private fun readUtf8Body(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        return if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val read = session.inputStream.read(buffer, totalRead, contentLength - totalRead)
                if (read == -1) break
                totalRead += read
            }
            String(buffer, 0, totalRead, StandardCharsets.UTF_8)
        } else {
            val files = HashMap<String, String>()
            session.parseBody(files)
            files["postData"] ?: ""
        }
    }
}
