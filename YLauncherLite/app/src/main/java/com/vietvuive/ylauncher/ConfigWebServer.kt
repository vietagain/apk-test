package com.vietvuive.ylauncher

import android.content.Context
import fi.iki.elonen.NanoHTTPD
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
                    val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
                    val body = if (contentLength > 0) {
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
}
