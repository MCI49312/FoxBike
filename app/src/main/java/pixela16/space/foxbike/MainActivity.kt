package pixela16.space.foxbike

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
    private var isTracking = false
    private var isPaused = false
    private var isKmh = true
    private var tripDistance = 0f // in meters
    private var lastLocation: Location? = null
    private var totalOdometer = 0f // in meters
    private var currentSpeedMs = 0f
    private var maxSpeedMs = 0f
    private var totalElevationGain = 0f
    private val trackedPoints = mutableListOf<GeoPoint>()

    private var tts: TextToSpeech? = null
    private var lastAnnouncedDistance = 0f

    // Stopwatch/Timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTracking && !isPaused) {
                updateTimeUI()
            }
            timerHandler.postDelayed(this, 1000)
        }
    }

    // Stopwatch state
    private var startTimeMillis = 0L
    private var totalPausedTimeMillis = 0L
    private var pauseStartTimeMillis = 0L

    private lateinit var tvSpeed: TextView
    private lateinit var tvSpeedUnit: TextView
    private lateinit var tvOdometer: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnPauseResume: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnMap: ImageButton
    private lateinit var btnStats: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var tvWeather: TextView
    private lateinit var tvWind: TextView

    private var lastWeatherLocation: String? = null
    private var lastWindUnit: String? = null
    private val weatherHandler = Handler(Looper.getMainLooper())
    private val weatherRunnable = object : Runnable {
        override fun run() {
            refreshWeather()
            weatherHandler.postDelayed(this, 10 * 60 * 1000) // 10 minutes
        }
    }

    private lateinit var viewAvgSpeed: View
    private lateinit var viewMaxSpeed: View
    private lateinit var viewTime: View
    private lateinit var viewTrip: View
    private lateinit var viewElevation: View
    private lateinit var viewCalories: View

    private var currentLanguage: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startTracking()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        currentLanguage = prefs.getString("language", "en")
        updateLocale(currentLanguage!!)

        if (prefs.getBoolean("darkModeManual", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        loadOdometer()
        checkServiceReminder()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocationData(location)
                }
            }
        }
        timerHandler.post(timerRunnable)
        weatherHandler.post(weatherRunnable)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    private fun updateLocale(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "en")
        if (lang != currentLanguage) {
            recreate()
            return
        }

        isKmh = prefs.getBoolean("isKmh", true)
        val useMaps = prefs.getBoolean("useMaps", true)
        btnMap.visibility = if (useMaps) View.VISIBLE else View.GONE
        
        val weatherLoc = prefs.getString("weatherLocation", "Budapest")
        val windUnit = prefs.getString("windUnit", "km/h")
        if (weatherLoc != lastWeatherLocation || windUnit != lastWindUnit) {
            refreshWeather()
        }

        loadOdometer()
        updateUI()
    }

    private fun initViews() {
        tvSpeed = findViewById(R.id.tvSpeed)
        tvSpeedUnit = findViewById(R.id.tvSpeedUnit)
        tvOdometer = findViewById(R.id.tvOdometer)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnPauseResume = findViewById(R.id.btnPauseResume)
        btnSettings = findViewById(R.id.btnSettings)
        btnMap = findViewById(R.id.btnMap)
        btnStats = findViewById(R.id.btnStats)
        btnHistory = findViewById(R.id.btnHistory)
        tvWeather = findViewById(R.id.tvWeather)
        tvWind = findViewById(R.id.tvWind)

        viewAvgSpeed = findViewById(R.id.statAvgSpeed)
        viewMaxSpeed = findViewById(R.id.statMaxSpeed)
        viewTime = findViewById(R.id.statTime)
        viewTrip = findViewById(R.id.statTrip)
        viewElevation = findViewById(R.id.statElevation)
        viewCalories = findViewById(R.id.statCalories)

        setStatLabel(viewAvgSpeed, getString(R.string.avg_speed))
        setStatLabel(viewMaxSpeed, getString(R.string.max_speed))
        setStatLabel(viewTime, getString(R.string.time))
        setStatLabel(viewTrip, getString(R.string.trip_label))
        setStatLabel(viewElevation, getString(R.string.elevation))
        setStatLabel(viewCalories, getString(R.string.calories))

        btnStartStop.setOnClickListener {
            if (isTracking) {
                showStopConfirmation()
            } else {
                startTracking()
            }
        }

        btnPauseResume.setOnClickListener {
            if (isPaused) resumeTracking() else pauseTracking()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putParcelableArrayListExtra("points", ArrayList(trackedPoints))
            startActivity(intent)
        }
        
        btnStats.setOnClickListener {
            showStatsDialog()
        }
        
        btnHistory.setOnClickListener {
            val popup = android.widget.PopupMenu(this, it)
            popup.menu.add(getString(R.string.export_gpx))
            popup.setOnMenuItemClickListener { item ->
                if (item.title == getString(R.string.export_gpx)) {
                    exportGpx()
                }
                true
            }
            popup.show()
        }
        
        updateUI()
    }

    private fun setStatLabel(view: View, label: String) {
        view.findViewById<TextView>(R.id.tvLabel).text = label
    }

    private fun setStatValue(view: View, value: String) {
        view.findViewById<TextView>(R.id.tvValue).text = value
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        
        isTracking = true
        isPaused = false
        tripDistance = 0f
        maxSpeedMs = 0f
        totalElevationGain = 0f
        lastLocation = null
        trackedPoints.clear()
        startTimeMillis = System.currentTimeMillis()
        totalPausedTimeMillis = 0
        lastAnnouncedDistance = 0f
        btnStartStop.text = getString(R.string.stop_button)
        btnPauseResume.visibility = View.VISIBLE
        btnPauseResume.text = getString(R.string.pause)
        startLocationUpdates()
    }

    private fun stopTracking() {
        isTracking = false
        isPaused = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        btnStartStop.text = getString(R.string.start_button)
        btnPauseResume.visibility = View.GONE
        saveOdometer()
        checkServiceReminder()
        currentSpeedMs = 0f
        updateUI()
    }

    private fun pauseTracking() {
        isPaused = true
        pauseStartTimeMillis = System.currentTimeMillis()
        btnPauseResume.text = getString(R.string.resume)
    }

    private fun resumeTracking() {
        isPaused = false
        totalPausedTimeMillis += System.currentTimeMillis() - pauseStartTimeMillis
        btnPauseResume.text = getString(R.string.pause)
    }

    private fun showStopConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_stop_title)
            .setMessage(R.string.confirm_stop_message)
            .setPositiveButton(R.string.yes) { _, _ -> stopTracking() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun updateLocationData(location: Location) {
        if (!isTracking || isPaused) return

        currentSpeedMs = location.speed
        if (currentSpeedMs > maxSpeedMs) maxSpeedMs = currentSpeedMs
        
        trackedPoints.add(GeoPoint(location.latitude, location.longitude))
        
        if (lastLocation != null) {
            val distance = lastLocation!!.distanceTo(location)
            tripDistance += distance
            totalOdometer += distance
            
            val elevationDiff = location.altitude - lastLocation!!.altitude
            if (elevationDiff > 0) totalElevationGain += elevationDiff.toFloat()
        }
        lastLocation = location
        
        handleVoiceFeedback()
        updateUI()
    }

    private fun handleVoiceFeedback() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("voiceFeedback", false)) return

        // Announce every 1 unit (km or mile)
        val distanceUnitValue = if (isKmh) 1000f else 1609.34f
        if (tripDistance - lastAnnouncedDistance >= distanceUnitValue) {
            val distance = (tripDistance / distanceUnitValue).toInt()
            val speed = if (isKmh) currentSpeedMs * 3.6f else currentSpeedMs * 2.23694f
            val unitName = if (isKmh) "kilometers" else "miles"
            val text = String.format(Locale.getDefault(), "%d %s. Speed %.1f", distance, unitName, speed)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastAnnouncedDistance = tripDistance
        }
    }

    private fun checkServiceReminder() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("serviceReminder", true)) return
        
        val lastServiceOdo = prefs.getFloat("lastServiceOdo", 0f)
        val intervalKm = prefs.getInt("serviceIntervalKm", 500)
        val message = prefs.getString("serviceMessage", getString(R.string.oil_chain_reminder))
        
        if (totalOdometer - lastServiceOdo >= intervalKm * 1000f) {
            AlertDialog.Builder(this)
                .setTitle(R.string.service_reminder)
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> 
                    prefs.edit().putFloat("lastServiceOdo", totalOdometer).apply()
                }
                .show()
        }
    }

    private fun refreshWeather() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val location = prefs.getString("weatherLocation", "Budapest") ?: "Budapest"
        val cityName = location.split(",")[0].trim().replace(" ", "+")
        val windUnit = prefs.getString("windUnit", "km/h")
        lastWeatherLocation = location
        lastWindUnit = windUnit
        
        thread {
            try {
                val urlString = "https://wttr.in/$cityName?format=j1"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                
                val current = json.getJSONArray("current_condition").getJSONObject(0)
                val temp = current.getDouble("temp_C")
                val windKmph = current.getDouble("windspeedKmph")
                val description = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value")

                val displayWindSpeed = when(windUnit) {
                    "mph" -> windKmph * 0.621371
                    "m/s" -> windKmph / 3.6
                    else -> windKmph
                }

                runOnUiThread {
                    tvWeather.text = String.format(Locale.getDefault(), "%s: %.1f°C (%s)", location.split(",")[0], temp, description)
                    tvWind.text = String.format(Locale.getDefault(), "%s: %.1f %s", getString(R.string.wind), displayWindSpeed, windUnit)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvWeather.text = "Weather error"
                }
            }
        }
    }

    private fun exportGpx() {
        if (trackedPoints.isEmpty()) return
        
        val gpx = StringBuilder()
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        gpx.append("<gpx version=\"1.1\" creator=\"FoxBike\">\n")
        gpx.append("<trk><trkseg>\n")
        for (point in trackedPoints) {
            gpx.append("<trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\"></trkpt>\n")
        }
        gpx.append("</trkseg></trk>\n")
        gpx.append("</gpx>")

        try {
            val fileName = "FoxBike_${System.currentTimeMillis()}.gpx"
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            FileOutputStream(file).use { it.write(gpx.toString().toByteArray()) }
            Toast.makeText(this, "${getString(R.string.gpx_exported)}: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getElapsedTimeSeconds(): Long {
        if (!isTracking) return 0
        val now = if (isPaused) pauseStartTimeMillis else System.currentTimeMillis()
        return (now - startTimeMillis - totalPausedTimeMillis) / 1000
    }

    private fun updateTimeUI() {
        val seconds = getElapsedTimeSeconds()
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        setStatValue(viewTime, String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s))
        
        updateCaloriesUI(seconds)
    }

    private fun updateCaloriesUI(seconds: Long) {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val weight = prefs.getString("weight", "70")?.toFloatOrNull() ?: 70f
        
        val speedKmh = currentSpeedMs * 3.6f
        val met = when {
            speedKmh < 16 -> 4.0f
            speedKmh < 19 -> 6.8f
            speedKmh < 22 -> 8.0f
            speedKmh < 25 -> 10.0f
            else -> 12.0f
        }
        val calories = met * weight * (seconds / 3600f)
        setStatValue(viewCalories, String.format(Locale.getDefault(), "%.0f kcal", calories))
    }

    private fun showStatsDialog() {
        val elapsedSeconds = getElapsedTimeSeconds()
        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)

        val avgSpeedMs = if (elapsedSeconds > 0) tripDistance / elapsedSeconds else 0f
        val avgSpeed = if (isKmh) avgSpeedMs * 3.6f else avgSpeedMs * 2.23694f
        val maxSpeed = if (isKmh) maxSpeedMs * 3.6f else maxSpeedMs * 2.23694f
        val unitLabel = if (isKmh) getString(R.string.unit_kmh) else getString(R.string.unit_mph)
        
        val distanceFactor = if (isKmh) 0.001f else 0.000621371f
        val distUnitLabel = if (isKmh) getString(R.string.unit_km) else getString(R.string.unit_mi)
        val elevUnitLabel = if (isKmh) "m" else "ft"
        val elevFactor = if (isKmh) 1f else 3.28084f

        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val weight = prefs.getString("weight", "70")?.toFloatOrNull() ?: 70f
        val speedKmh = currentSpeedMs * 3.6f
        val met = when {
            speedKmh < 16 -> 4.0f
            speedKmh < 19 -> 6.8f
            speedKmh < 22 -> 8.0f
            speedKmh < 25 -> 10.0f
            else -> 12.0f
        }
        val calories = met * weight * (elapsedSeconds / 3600f)

        val statsMessage = """
            ${getString(R.string.time)}: $timeStr
            ${getString(R.string.trip_label)}: ${String.format(Locale.getDefault(), "%.2f %s", tripDistance * distanceFactor, distUnitLabel)}
            ${getString(R.string.avg_speed)}: ${String.format(Locale.getDefault(), "%.1f %s", avgSpeed, unitLabel)}
            ${getString(R.string.max_speed)}: ${String.format(Locale.getDefault(), "%.1f %s", maxSpeed, unitLabel)}
            ${getString(R.string.elevation)}: ${String.format(Locale.getDefault(), "%.0f %s", totalElevationGain * elevFactor, elevUnitLabel)}
            ${getString(R.string.calories)}: ${String.format(Locale.getDefault(), "%.0f kcal", calories)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(R.string.statistics)
            .setMessage(statsMessage)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateUI() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val isDemoMode = prefs.getBoolean("demoMode", false)

        val speedMs = if (isDemoMode) prefs.getFloat("fakeSpeed", 0f) / 3.6f else currentSpeedMs
        val displayTrip = if (isDemoMode) prefs.getFloat("fakeTrip", 0f) * 1000f else tripDistance
        val displayOdo = if (isDemoMode) prefs.getFloat("fakeOdometer", 0f) * 1000f else totalOdometer

        val speed = if (isKmh) speedMs * 3.6f else speedMs * 2.23694f
        val maxSpeed = if (isKmh) maxSpeedMs * 3.6f else maxSpeedMs * 2.23694f
        val distanceFactor = if (isKmh) 0.001f else 0.000621371f
        val unitLabel = if (isKmh) getString(R.string.unit_kmh) else getString(R.string.unit_mph)
        val distUnitLabel = if (isKmh) getString(R.string.unit_km) else getString(R.string.unit_mi)
        val elevUnitLabel = if (isKmh) "m" else "ft"
        val elevFactor = if (isKmh) 1f else 3.28084f

        tvSpeed.text = String.format(Locale.getDefault(), "%.1f", speed)
        tvSpeedUnit.text = unitLabel
        
        val elapsedSeconds = getElapsedTimeSeconds()
        val avgSpeedMs = if (elapsedSeconds > 0) tripDistance / elapsedSeconds else 0f
        val avgSpeed = if (isKmh) avgSpeedMs * 3.6f else avgSpeedMs * 2.23694f
        
        setStatValue(viewAvgSpeed, String.format(Locale.getDefault(), "%.1f", avgSpeed))
        setStatValue(viewMaxSpeed, String.format(Locale.getDefault(), "%.1f", maxSpeed))
        setStatValue(viewTrip, String.format(Locale.getDefault(), "%.2f %s", displayTrip * distanceFactor, distUnitLabel))
        setStatValue(viewElevation, String.format(Locale.getDefault(), "%.0f %s", totalElevationGain * elevFactor, elevUnitLabel))
        
        tvOdometer.text = String.format(Locale.getDefault(), "ODO: %.1f %s", displayOdo * distanceFactor, distUnitLabel)
        
        if (!isTracking) {
            setStatValue(viewTime, "00:00:00")
            setStatValue(viewCalories, "0 kcal")
        }
    }

    private fun loadOdometer() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        totalOdometer = prefs.getFloat("totalOdometer", 0f)
    }

    private fun saveOdometer() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("totalOdometer", totalOdometer).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        weatherHandler.removeCallbacks(weatherRunnable)
        tts?.stop()
        tts?.shutdown()
        if (isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
