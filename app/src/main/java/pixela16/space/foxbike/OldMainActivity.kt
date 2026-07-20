package pixela16.space.foxbike

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
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

class OldMainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

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
    private var totalCalories = 0f
    private val trackedPoints = mutableListOf<GeoPoint>()

    private var tts: TextToSpeech? = null
    private var lastAnnouncedDistance = 0f

    private var clickCount = 0
    private var lastClickTime = 0L

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

    private var startTimeMillis = 0L
    private var totalPausedTimeMillis = 0L
    private var pauseStartTimeMillis = 0L

    private lateinit var tvSpeed: TextView
    private lateinit var tvSpeedUnit: TextView
    private lateinit var tvOdometer: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnPauseResume: Button
    private lateinit var tvWeather: TextView
    private lateinit var tvWind: TextView

    private lateinit var viewAvgSpeed: View
    private lateinit var viewMaxSpeed: View
    private lateinit var viewTime: View
    private lateinit var viewTrip: View
    private lateinit var viewElevation: View
    private lateinit var viewCalories: View

    private var lastWeatherLocation: String? = null
    private var lastWindUnit: String? = null
    private val weatherHandler = Handler(Looper.getMainLooper())
    private val weatherRunnable = object : Runnable {
        override fun run() {
            refreshWeather()
            weatherHandler.postDelayed(this, 10 * 60 * 1000)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startTracking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "en") ?: "en"
        updateLocale(lang)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_old)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_old_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        loadOdometer()
        
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

    private fun initViews() {
        tvSpeed = findViewById(R.id.tvSpeed)
        tvSpeedUnit = findViewById(R.id.tvSpeedUnit)
        tvOdometer = findViewById(R.id.tvOdometer)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnPauseResume = findViewById(R.id.btnPauseResume)
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

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnMap).setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putParcelableArrayListExtra("points", ArrayList(trackedPoints))
            startActivity(intent)
        }

        findViewById<ImageButton>(R.id.btnStats).setOnClickListener {
            showSummaryStats()
        }

        btnStartStop.setOnClickListener {
            if (isTracking) showStopConfirmation() else startTracking()
        }

        btnPauseResume.setOnClickListener {
            if (isPaused) resumeTracking() else pauseTracking()
        }

        tvSpeed.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) clickCount++ else clickCount = 1
            lastClickTime = currentTime
            if (clickCount == 7) {
                showCodeDialog()
                clickCount = 0
            }
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
        totalCalories = 0f
        lastLocation = null
        trackedPoints.clear()
        startTimeMillis = System.currentTimeMillis()
        totalPausedTimeMillis = 0
        lastAnnouncedDistance = 0f
        btnStartStop.text = getString(R.string.stop_button)
        btnPauseResume.visibility = View.VISIBLE
        btnPauseResume.text = getString(R.string.pause)
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

    private fun updateUI() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        isKmh = prefs.getBoolean("isKmh", true)
        
        val speed = if (isKmh) currentSpeedMs * 3.6f else currentSpeedMs * 2.23694f
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
        setStatValue(viewTrip, String.format(Locale.getDefault(), "%.2f %s", tripDistance * distanceFactor, distUnitLabel))
        setStatValue(viewElevation, String.format(Locale.getDefault(), "%.0f %s", totalElevationGain * elevFactor, elevUnitLabel))
        
        tvOdometer.text = String.format(Locale.getDefault(), "ODO: %.1f %s", (totalOdometer + tripDistance) * distanceFactor, distUnitLabel)
        
        if (!isTracking) {
            setStatValue(viewTime, "00:00:00")
            setStatValue(viewCalories, "0 kcal")
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
        
        val weight = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE).getString("weight", "70")?.toFloatOrNull() ?: 70f
        val speedKmh = currentSpeedMs * 3.6f
        
        if (speedKmh > 2.0f) {
            val met = when {
                speedKmh < 16 -> 4.0f
                speedKmh < 19 -> 6.8f
                speedKmh < 22 -> 8.0f
                speedKmh < 25 -> 10.0f
                else -> 12.0f
            }
            totalCalories += met * weight * (1.0f / 3600.0f)
        }
        setStatValue(viewCalories, String.format(Locale.getDefault(), "%.0f kcal", totalCalories))
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

    private fun refreshWeather() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val location = prefs.getString("weatherLocation", "Budapest") ?: "Budapest"
        val cityName = location.split(",")[0].trim().replace(" ", "+")
        val windUnit = prefs.getString("windUnit", "km/h")
        val tempUnit = prefs.getString("tempUnit", "°C")

        thread {
            try {
                val response = URL("https://wttr.in/$cityName?format=j1").readText()
                val json = JSONObject(response)
                val current = json.getJSONArray("current_condition").getJSONObject(0)
                val tempC = current.getDouble("temp_C")
                val windKmph = current.getDouble("windspeedKmph")
                
                val displayTemp = if (tempUnit == "°F") (tempC * 9/5) + 32 else tempC
                val displayWind = when(windUnit) {
                    "mph" -> windKmph * 0.621371
                    "m/s" -> windKmph / 3.6
                    else -> windKmph
                }

                runOnUiThread {
                    tvWeather.text = String.format(Locale.getDefault(), "%.1f%s", displayTemp, tempUnit)
                    tvWind.text = String.format(Locale.getDefault(), "%.1f %s", displayWind, windUnit)
                }
            } catch (e: Exception) { runOnUiThread { tvWeather.text = "Error" } }
        }
    }

    private fun showSummaryStats() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val isKmh = prefs.getBoolean("isKmh", true)
        val totalOdo = prefs.getFloat("totalOdometer", 0f)
        val factor = if (isKmh) 0.001f else 0.000621371f
        val unit = if (isKmh) "km" else "mi"
        
        val msg = String.format(Locale.getDefault(), "Total ODO: %.1f %s\nMax Speed: %.1f %s", 
            (totalOdo + tripDistance) * factor, unit,
            if (isKmh) maxSpeedMs * 3.6f else maxSpeedMs * 2.23694f,
            if (isKmh) "km/h" else "mph")
        
        AlertDialog.Builder(this)
            .setTitle(R.string.statistics)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showStopConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_stop_title)
            .setMessage(R.string.confirm_stop_message)
            .setPositiveButton(R.string.yes) { _, _ -> 
                isTracking = false
                fusedLocationClient.removeLocationUpdates(locationCallback)
                val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
                prefs.edit().putFloat("totalOdometer", prefs.getFloat("totalOdometer", 0f) + tripDistance).apply()
                tripDistance = 0f
                currentSpeedMs = 0f
                btnStartStop.text = getString(R.string.start_button)
                btnPauseResume.visibility = View.GONE
                updateUI()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun loadOdometer() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        totalOdometer = prefs.getFloat("totalOdometer", 0f)
    }

    private fun showCodeDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle(R.string.enter_code)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val code = input.text.toString()
                val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
                when (code) {
                    "1900" -> {
                        prefs.edit { putBoolean("demoMode", true) }
                        Toast.makeText(this, R.string.demo_mode_enabled, Toast.LENGTH_SHORT).show()
                    }
                    "1901" -> {
                        prefs.edit { putBoolean("demoMode", false) }
                        Toast.makeText(this, R.string.demo_mode_disabled, Toast.LENGTH_SHORT).show()
                    }
                    "1902" -> {
                        showSetOdometerDialog()
                    }
                    "1903" -> {
                        prefs.edit { putFloat("totalOdometer", 0f) }
                        Toast.makeText(this, R.string.reset_odometer, Toast.LENGTH_SHORT).show()
                        loadOdometer()
                        updateUI()
                    }
                    "1904" -> {
                        val isDev = !prefs.getBoolean("useDeveloperBranch", false)
                        prefs.edit { putBoolean("useDeveloperBranch", isDev) }
                        Toast.makeText(this, "Branch switched to ${if (isDev) "developer" else "main"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSetOdometerDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Value in km"
        AlertDialog.Builder(this)
            .setTitle("Set Odometer")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val value = input.text.toString().toFloatOrNull() ?: 0f
                val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
                prefs.edit { putFloat("totalOdometer", value * 1000f) }
                Toast.makeText(this, "Odometer set to $value km", Toast.LENGTH_SHORT).show()
                loadOdometer()
                updateUI()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateLocale(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        tts?.stop()
        tts?.shutdown()
        if (isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
