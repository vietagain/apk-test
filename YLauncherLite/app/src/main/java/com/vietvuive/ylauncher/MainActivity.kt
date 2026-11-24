package com.vietvuive.ylauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var webServer: ConfigWebServer? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var headerCard: LinearLayout
    private lateinit var tvTime: TextView
    private lateinit var tvDateSolar: TextView
    private lateinit var tvDateLunar: TextView
    private lateinit var tvStatusHa: TextView

    private lateinit var tvWeatherState: TextView
    private lateinit var tvOutdoorTemp: TextView
    private lateinit var tvSensorTemp: TextView
    private lateinit var tvSensorHum: TextView
    private lateinit var tvSensorPower: TextView

    private lateinit var gridTiles: GridLayout
    private lateinit var bannerWebConfig: LinearLayout
    private lateinit var tvWebUrl: TextView

    private var currentConfig: AppConfig? = null
    private var latestStates: Map<String, EntityState> = emptyMap()
    private val tileViews = HashMap<String, View>()
    private var currentAlpha: Int = 204

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val cfg = currentConfig ?: return
            ioExecutor.execute {
                val states = HaClient.fetchAllStates(cfg.haUrl, cfg.haToken)
                mainHandler.post {
                    if (states != null) {
                        latestStates = states
                        tvStatusHa.text = "● HA Online"
                        tvStatusHa.setTextColor(Color.parseColor("#22C55E"))
                        updateAllUiValues()
                    } else {
                        tvStatusHa.text = "● Mất kết nối HA"
                        tvStatusHa.setTextColor(Color.parseColor("#EF4444"))
                    }
                }
            }
            mainHandler.postDelayed(this, (cfg.refreshIntervalSec * 1000L))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        bindViews()
        reloadConfigAndBuildTiles()
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.root_layout)
        headerCard = findViewById(R.id.header_card)
        tvTime = findViewById(R.id.tv_time)
        tvDateSolar = findViewById(R.id.tv_date_solar)
        tvDateLunar = findViewById(R.id.tv_date_lunar)
        tvStatusHa = findViewById(R.id.tv_status_ha)

        tvWeatherState = findViewById(R.id.tv_weather_state)
        tvOutdoorTemp = findViewById(R.id.tv_outdoor_temp)
        tvSensorTemp = findViewById(R.id.tv_sensor_temp)
        tvSensorHum = findViewById(R.id.tv_sensor_hum)
        tvSensorPower = findViewById(R.id.tv_sensor_power)

        gridTiles = findViewById(R.id.grid_tiles)
        bannerWebConfig = findViewById(R.id.banner_web_config)
        tvWebUrl = findViewById(R.id.tv_web_url)

        findViewById<Button>(R.id.btn_web_config).setOnClickListener {
            toggleWebServer()
        }

        findViewById<Button>(R.id.btn_apps).setOnClickListener {
            showAppDrawerDialog()
        }

        findViewById<Button>(R.id.btn_key_map).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Bật mục 'Y Smart Home - Giữ Tăng Âm Lượng'", Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadConfigAndBuildTiles() {
        val cfg = ConfigManager.loadConfig(this)
        currentConfig = cfg
        currentAlpha = (cfg.tileOpacity * 255 / 100).coerceIn(0, 255)
        applyWallpaperAndTheme(cfg.theme)
        headerCard.background = makeCardBg("#263241", "#17202A")
        buildGridTiles(cfg)
        updateAllUiValues()
    }

    private fun applyWallpaperAndTheme(theme: String) {
        val wpFile = File(filesDir, "wallpaper.jpg")
        if (wpFile.exists()) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(wpFile.absolutePath, opts)
                val reqWidth = resources.displayMetrics.widthPixels.coerceAtLeast(800)
                var sample = 1
                while (opts.outWidth / sample > reqWidth) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeFile(wpFile.absolutePath, decodeOpts)
                if (bmp != null) {
                    rootLayout.background = BitmapDrawable(resources, bmp)
                    return
                }
            } catch (_: Exception) {
            }
        }

        val bgColor = when (theme) {
            "green" -> Color.parseColor("#0B1914")
            "scifi" -> Color.parseColor("#090D16")
            else -> Color.parseColor("#0F141A")
        }
        rootLayout.setBackgroundColor(bgColor)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun buildGridTiles(cfg: AppConfig) {
        gridTiles.removeAllViews()
        tileViews.clear()

        val totalCols = if (resources.configuration.screenWidthDp >= 520) 4 else 2
        gridTiles.columnCount = totalCols

        val baseRowHeight = dpToPx(84)
        val marginPx = dpToPx(5)

        for (tile in cfg.tiles) {
            val view = when (tile.type) {
                "gate" -> createGateTileView(tile)
                "sensor" -> createSensorTileView(tile)
                "solar_flow" -> createSolarFlowTileView(tile)
                else -> createSwitchTileView(tile)
            }

            var colSpan = when (tile.size) {
                "small" -> 1
                "medium" -> 2
                "large" -> 2
                "wide" -> totalCols
                else -> 2
            }
            if (colSpan > totalCols) colSpan = totalCols

            // Biểu đồ Solar mặc định cao gấp đôi để hiển thị rõ 3 vòng tròn
            val rowSpan = if (tile.size == "large" || tile.type == "solar_flow") 2 else 1

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = if (rowSpan == 2) (baseRowHeight * 2 + marginPx * 2) else baseRowHeight
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, colSpan, colSpan.toFloat())
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, rowSpan)
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            gridTiles.addView(view, params)
            tileViews[tile.id] = view
        }
    }

    private fun applyAlphaToHex(hexColor: String, alpha: Int): Int {
        val base = Color.parseColor(hexColor)
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    private fun makeCardBg(strokeColor: String = "#263241", fillColor: String = "#17202A"): GradientDrawable {
        val borderAlpha = (currentAlpha + 50).coerceIn(40, 255)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(applyAlphaToHex(fillColor, currentAlpha))
            setStroke(2, applyAlphaToHex(strokeColor, borderAlpha))
        }
    }

    private fun createSolarFlowTileView(tile: TileConfig): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            background = makeCardBg("#334155", "#111827")
        }
        val flowView = SolarFlowView(this).apply {
            tag = "flow_view"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        box.addView(flowView)
        return box
    }

    private fun createSwitchTileView(tile: TileConfig): View {
        val isLarge = tile.size == "large"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            background = makeCardBg()
        }
        val tvTitle = TextView(this).apply {
            text = tile.name
            textSize = if (isLarge) 19f else 15f
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            maxLines = 2
        }
        val tvState = TextView(this).apply {
            tag = "state"
            text = "Đang tải..."
            textSize = if (isLarge) 16f else 12f
            setTextColor(Color.parseColor("#CBD5E1"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            setPadding(0, dpToPx(4), 0, 0)
        }
        box.addView(tvTitle)
        box.addView(tvState)

        box.setOnClickListener {
            val cfg = currentConfig ?: return@setOnClickListener
            tvState.text = "Đang gửi..."
            ioExecutor.execute {
                val ok = HaClient.callService(cfg.haUrl, cfg.haToken, tile.entityId, "toggle")
                if (ok) {
                    Thread.sleep(400)
                    val states = HaClient.fetchAllStates(cfg.haUrl, cfg.haToken)
                    mainHandler.post {
                        if (states != null) {
                            latestStates = states
                            updateAllUiValues()
                        }
                    }
                }
            }
        }
        return box
    }

    private fun createSensorTileView(tile: TileConfig): View {
        val isLarge = tile.size == "large"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            background = makeCardBg("#1E3A5F", "#122338")
        }
        val tvTitle = TextView(this).apply {
            text = tile.name
            textSize = if (isLarge) 18f else 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            maxLines = 1
        }
        val tvVal = TextView(this).apply {
            tag = "state"
            text = "-- ${tile.unit}"
            textSize = if (isLarge) 32f else 19f
            setTextColor(Color.parseColor("#38BDF8"))
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            setPadding(0, dpToPx(4), 0, 0)
        }
        box.addView(tvTitle)
        box.addView(tvVal)
        return box
    }

    private fun createGateTileView(tile: TileConfig): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            background = makeCardBg("#3B2F4A", "#1E1926")
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val tvTitle = TextView(this).apply {
            text = tile.name
            textSize = 14f
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvState = TextView(this).apply {
            tag = "state"
            text = "--"
            textSize = 12f
            setTextColor(Color.parseColor("#C084FC"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        header.addView(tvTitle)
        header.addView(tvState)
        box.addView(header)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(6), 0, 0)
        }
        val actions = listOf("Mở" to "open", "Dừng" to "stop", "Đóng" to "close")
        for ((label, cmd) in actions) {
            val btn = Button(this).apply {
                text = label
                textSize = 11f
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = makeCardBg("#475569", "#334155")
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply {
                    setMargins(4, 0, 4, 0)
                }
                setOnClickListener {
                    val cfg = currentConfig ?: return@setOnClickListener
                    tvState.text = "Gửi: $label"
                    ioExecutor.execute {
                        HaClient.callService(cfg.haUrl, cfg.haToken, tile.entityId, cmd)
                    }
                }
            }
            btnRow.addView(btn)
        }
        box.addView(btnRow)
        return box
    }

    private fun parseWatts(entityId: String): Double {
        val st = latestStates[entityId] ?: return 0.0
        val v = st.state.toDoubleOrNull() ?: return 0.0
        val unit = st.attributes.optString("unit_of_measurement", "").lowercase()
        return if (unit == "kw") v * 1000.0 else v
    }

    private fun updateAllUiValues() {
        val cfg = currentConfig ?: return

        val wState = latestStates[cfg.weatherEntity]
        if (wState != null) {
            tvWeatherState.text = translateWeather(wState.state)
            val temp = formatNumber(wState.attributes.optString("temperature", "--"))
            tvOutdoorTemp.text = "Ngoài trời: $temp°C"
        }

        val tState = formatNumber(latestStates[cfg.tempEntity]?.state)
        val hState = formatNumber(latestStates[cfg.humEntity]?.state)
        val pState = formatNumber(latestStates[cfg.powerEntity]?.state)

        tvSensorTemp.text = "🌡 $tState°C"
        tvSensorHum.text = "💧 $hState%"
        tvSensorPower.text = "⚡ ${pState}W"

        for (tile in cfg.tiles) {
            val view = tileViews[tile.id] ?: continue

            if (tile.type == "solar_flow") {
                val flowView = view.findViewWithTag<SolarFlowView>("flow_view") ?: continue
                flowView.pvWatts = parseWatts(tile.pvEntity)
                flowView.loadWatts = parseWatts(tile.loadEntity)
                val rawBatW = parseWatts(tile.batPowerEntity)
                flowView.batteryWatts = if (tile.invertBatPower) -rawBatW else rawBatW
                flowView.batterySoc = formatNumber(latestStates[tile.batSocEntity]?.state)
                flowView.invalidate()
                continue
            }

            val tvState = view.findViewWithTag<TextView>("state") ?: continue
            val stObj = latestStates[tile.entityId]
            val rawState = stObj?.state ?: "unavailable"

            when (tile.type) {
                "sensor" -> {
                    val formattedVal = formatNumber(rawState)
                    val unit = tile.unit.ifBlank { stObj?.attributes?.optString("unit_of_measurement", "") ?: "" }
                    tvState.text = "$formattedVal $unit".trim()
                }
                "gate" -> {
                    tvState.text = when (rawState) {
                        "open" -> "Đang mở"
                        "opening" -> "Đang chạy mở..."
                        "closed" -> "Đã đóng"
                        "closing" -> "Đang chạy đóng..."
                        "unavailable", "unknown" -> "--"
                        else -> rawState
                    }
                }
                else -> {
                    val isOn = rawState == "on" || rawState == "heat" || rawState == "cool" || rawState == "auto"
                    tvState.text = if (isOn) "BẬT ($rawState)" else "TẮT"
                    tvState.setTextColor(if (isOn) Color.parseColor("#4ADE80") else Color.parseColor("#CBD5E1"))
                    view.background = if (isOn) makeCardBg("#22C55E", "#132A1E") else makeCardBg()
                }
            }
        }
    }

    private fun formatNumber(raw: String?): String {
        if (raw == null || raw == "unavailable" || raw == "unknown" || raw.isBlank()) return "--"
        val num = raw.toDoubleOrNull() ?: return raw
        return if (num % 1.0 == 0.0) {
            num.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", num)
        }
    }

    private fun translateWeather(state: String): String {
        return when (state.lowercase()) {
            "sunny", "clear-night" -> "☀️ Trời quang"
            "partlycloudy" -> "⛅ Ít mây"
            "cloudy" -> "☁️ Nhiều mây"
            "rainy" -> "🌧 Mưa"
            "pouring" -> "⛈ Mưa to"
            "lightning", "lightning-rainy" -> "🌩 Giông sét"
            "fog" -> "🌫 Sương mù"
            else -> "🌤 $state"
        }
    }

    private fun updateClock() {
        val now = Date()
        tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        tvDateSolar.text = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")).format(now)
        tvDateLunar.text = LunarCalendar.getTodayLunarString()
    }

    private fun toggleWebServer() {
        if (webServer != null) {
            webServer?.stop()
            webServer = null
            bannerWebConfig.visibility = View.GONE
            Toast.makeText(this, "Đã tắt máy chủ cấu hình Web", Toast.LENGTH_SHORT).show()
        } else {
            try {
                val server = ConfigWebServer(this, 8080) {
                    mainHandler.post { reloadConfigAndBuildTiles() }
                }
                server.start()
                webServer = server
                val ip = getLocalIpAddress()
                tvWebUrl.text = "Mở trình duyệt truy cập: http://$ip:8080 (Tự tắt sau 15 phút)"
                bannerWebConfig.visibility = View.VISIBLE
                mainHandler.postDelayed({
                    if (webServer != null) toggleWebServer()
                }, 15 * 60 * 1000L)
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi bật Web Server: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wm.connectionInfo.ipAddress
        return if (ipInt != 0) Formatter.formatIpAddress(ipInt) else "192.168.1.xxx"
    }

    private fun showAppDrawerDialog() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Ứng dụng trên máy")
            .setItems(names) { _, which ->
                val pkg = apps[which].activityInfo.packageName
                val launch = pm.getLaunchIntentForPackage(pkg)
                if (launch != null) startActivity(launch)
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(clockRunnable)
        mainHandler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        webServer?.stop()
        ioExecutor.shutdownNow()
    }
}
