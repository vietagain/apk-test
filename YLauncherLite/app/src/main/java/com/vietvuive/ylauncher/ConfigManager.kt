package com.vietvuive.ylauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TileConfig(
    val id: String,
    val type: String, // "switch", "gate", "sensor", "solar_flow"
    val size: String, // "small", "medium", "wide", "large"
    val name: String,
    val entityId: String,
    val unit: String = "",
    val icon: String = "power",
    val pvEntity: String = "",
    val loadEntity: String = "",
    val batPowerEntity: String = "",
    val batSocEntity: String = "",
    val invertBatPower: Boolean = false
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
    val tileOpacity: Int,
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
            put("tempEntity", "")
            put("humEntity", "")
            put("powerEntity", "")
            put("theme", "dark")
            put("tileOpacity", 80)
            put("tiles", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "t_solar")
                    put("type", "solar_flow")
                    put("size", "large")
                    put("name", "Luồng Điện Solar")
                    put("entityId", "")
                    put("pvEntity", "sensor.pv_power")
                    put("loadEntity", "sensor.load_power")
                    put("batPowerEntity", "sensor.battery_power")
                    put("batSocEntity", "sensor.battery_soc")
                    put("invertBatPower", false)
                })
                put(JSONObject().apply {
                    put("id", "t1")
                    put("type", "switch")
                    put("size", "medium")
                    put("name", "Đèn Khách")
                    put("entityId", "light.phong_khach")
                })
                put(JSONObject().apply {
                    put("id", "t2")
                    put("type", "switch")
                    put("size", "medium")
                    put("name", "Quạt Trần")
                    put("entityId", "fan.quat_tran")
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
                    icon = item.optString("icon", "power"),
                    pvEntity = item.optString("pvEntity", ""),
                    loadEntity = item.optString("loadEntity", ""),
                    batPowerEntity = item.optString("batPowerEntity", ""),
                    batSocEntity = item.optString("batSocEntity", ""),
                    invertBatPower = item.optBoolean("invertBatPower", false)
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
