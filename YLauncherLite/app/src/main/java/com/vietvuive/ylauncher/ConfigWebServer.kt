package com.vietvuive.ylauncher

import android.content.Context
import fi.iki.elonen.NanoHTTPD

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
                val files = HashMap<String, String>()
                try {
                    session.parseBody(files)
                    val body = files["postData"] ?: ""
                    val ok = ConfigManager.saveRawJson(context, body)
                    if (ok) {
                        onConfigUpdated()
                        newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"ok\"}")
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"status\":\"invalid_json\"}")
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"status\":\"error\"}")
                }
            }
            else -> {
                val html = try {
                    context.assets.open("index.html").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "<h1>Lỗi không tìm thấy giao diện cấu hình</h1>"
                }
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            }
        }
    }
}
