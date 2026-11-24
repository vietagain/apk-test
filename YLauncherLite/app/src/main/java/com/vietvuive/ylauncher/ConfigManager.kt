package com.vietvuive.ylauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TileConfig(
    val id: String,
    val type: String, // "switch", "gate", "sensor"
    val size: String, // "small", "medium", "wide", "large"
    val name: String,
    val entityId: String,
    val unit: String = "",
    val icon: String = "power"
)

data class AppConfig(
    val haUrl: String,
    val haToken: String,
    val refreshIntervalSec: Int,
    val weatherEntity: String,
    val tempEntity: String,
    val humEntity: String,
    val powerEntity: String,
    val theme: String,
    val tileOpacity: Int, // 0 -> 100 (%)
    val tiles: List<TileConfig>
)

object ConfigManager {
    private const val PREF_NAME = "ylauncher_prefs"
    private const val KEY_JSON = "config_json"

    fun getDefaultJson(): String {
        return JSONObject().apply {
            put("haUrl", "http://192.168.1.100:8123")
            put("haToken", "")
            put("refreshIntervalSec", 3)
            put("weatherEntity", "weather.forecast_home")
            put("tempEntity", "sensor.nhiet_do_phong")
            put("humEntity", "sensor.do_am_phong")
            put("powerEntity", "sensor.cong_suat_tong")
            put("theme", "dark")
            put("tileOpacity", 80)
            put("tiles", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "t1")
                    put("type", "switch")
                    put("size", "small")
                    put("name", "Đèn Khách")
                    put("entityId", "light.phong_khach")
                })
                put(JSONObject().apply {
                    put("id", "t2")
                    put("type", "switch")
                    put("size", "small")
                    put("name", "Quạt Trần")
                    put("entityId", "fan.quat_tran")
                })
                put(JSONObject().apply {
                    put("id", "t3")
                    put("type", "sensor")
                    put("size", "large")
                    put("name", "Pin Solar")
                    put("entityId", "sensor.battery_soc")
                    put("unit", "%")
                })
                put(JSONObject().apply {
                    put("id", "t4")
                    put("type", "switch")
                    put("size", "medium")
                    put("name", "Điều Hòa")
                    put("entityId", "climate.dieu_hoa")
                })
                put(JSONObject().apply {
                    put("id", "t5")
                    put("type", "sensor")
                    put("size", "medium")
                    put("name", "Công Suất PV")
                    put("entityId", "sensor.pv_power")
                    put("unit", "W")
                })
                put(JSONObject().apply {
                    put("id", "t6")
                    put("type", "gate")
                    put("size", "wide")
                    put("name", "Cổng Chính")
                    put("entityId", "cover.cong_chinh")
                })
            })
        }.toString(2)
    }

    fun getRawJson(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_JSON, null) ?: getDefaultJson()
    }

    fun saveRawJson(context: Context, jsonStr: String): Boolean {
        return try {
            JSONObject(jsonStr)
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, jsonStr)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadConfig(context: Context): AppConfig {
        val obj = try {
            JSONObject(getRawJson(context))
        } catch (e: Exception) {
            JSONObject(getDefaultJson())
        }

        val tilesArr = obj.optJSONArray("tiles") ?: JSONArray()
        val list = mutableListOf<TileConfig>()
        for (i in 0 until tilesArr.length()) {
            val item = tilesArr.optJSONObject(i) ?: continue
            list.add(
                TileConfig(
                    id = item.optString("id", "tile_$i"),
                    type = item.optString("type", "switch"),
                    size = item.optString("size", "medium"),
                    name = item.optString("name", "Thiết bị"),
                    entityId = item.optString("entityId", ""),
                    unit = item.optString("unit", ""),
                    icon = item.optString("icon", "power")
                )
            )
        }

        return AppConfig(
            haUrl = obj.optString("haUrl", "http://192.168.1.100:8123").trimEnd('/'),
            haToken = obj.optString("haToken", "").trim(),
            refreshIntervalSec = obj.optInt("refreshIntervalSec", 3).coerceIn(1, 60),
            weatherEntity = obj.optString("weatherEntity", ""),
            tempEntity = obj.optString("tempEntity", ""),
            humEntity = obj.optString("humEntity", ""),
            powerEntity = obj.optString("powerEntity", ""),
            theme = obj.optString("theme", "dark"),
            tileOpacity = obj.optInt("tileOpacity", 80).coerceIn(0, 100),
            tiles = list
        )
    }
}
