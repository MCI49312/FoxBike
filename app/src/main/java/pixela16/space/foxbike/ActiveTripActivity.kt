package pixela16.space.foxbike

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

class ActiveTripActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
    private var isTracking = false
    private var isPaused = false
    private var isKmh = true
    private var tripDistance = 0f
    private var lastLocation: Location? = null
    private var currentSpeedMs = 0f
    private var maxSpeedMs = 0f
    private var totalElevationGain = 0f
    private var totalCalories = 0f
    private val trackedPoints = mutableListOf<GeoPoint>()

    private var startTimeMillis = 0L
    private var totalPausedTimeMillis = 0L
    private var pauseStartTimeMillis = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTracking && !isPaused) updateTimeUI()
            timerHandler.postDelayed(this, 1000)
        }
    }

    private lateinit var tvSpeed: TextView
    private lateinit var tvSpeedUnit: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvWind: TextView

    private lateinit var viewAvgSpeed: View
    private lateinit var viewMaxSpeed: View
    private lateinit var viewTime: View
    private lateinit var viewTrip: View
    private lateinit var viewElevation: View
    private lateinit var viewCalories: View

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) startTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_active_trip)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.active_trip_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) updateLocationData(location)
            }
        }

        startTracking()
        timerHandler.post(timerRunnable)
        refreshWeather()

        onBackPressedDispatcher.addCallback(this) {
            showStopConfirmation()
        }
    }

    private fun initViews() {
        tvSpeed = findViewById(R.id.tvActiveSpeed)
        tvSpeedUnit = findViewById(R.id.tvActiveSpeedUnit)
        tvWeather = findViewById(R.id.tvActiveWeather)
        tvWind = findViewById(R.id.tvActiveWind)

        viewAvgSpeed = findViewById(R.id.activeAvgSpeed)
        viewMaxSpeed = findViewById(R.id.activeMaxSpeed)
        viewTime = findViewById(R.id.activeTime)
        viewTrip = findViewById(R.id.activeTrip)
        viewElevation = findViewById(R.id.activeElevation)
        viewCalories = findViewById(R.id.activeCalories)

        setStatLabel(viewAvgSpeed, getString(R.string.avg_speed))
        setStatLabel(viewMaxSpeed, getString(R.string.max_speed))
        setStatLabel(viewTime, getString(R.string.time))
        setStatLabel(viewTrip, getString(R.string.trip_label))
        setStatLabel(viewElevation, getString(R.string.elevation))
        setStatLabel(viewCalories, getString(R.string.calories))

        findViewById<Button>(R.id.btnActivePause).setOnClickListener { if (isPaused) resumeTracking() else pauseTracking() }
        findViewById<Button>(R.id.btnActiveStop).setOnClickListener { showStopConfirmation() }
        findViewById<Button>(R.id.btnActiveStats).setOnClickListener { showDetailedStats() }
    }

    private fun setStatLabel(view: View, label: String) { view.findViewById<TextView>(R.id.tvLabel).text = label }
    private fun setStatValue(view: View, value: String) { view.findViewById<TextView>(R.id.tvValue).text = value }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        isTracking = true
        startTimeMillis = System.currentTimeMillis()
        totalCalories = 0f
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateLocationData(location: Location) {
        if (!isTracking || isPaused) return
        currentSpeedMs = location.speed
        if (currentSpeedMs > maxSpeedMs) maxSpeedMs = currentSpeedMs
        trackedPoints.add(GeoPoint(location.latitude, location.longitude))
        if (lastLocation != null) {
            val dist = lastLocation!!.distanceTo(location)
            tripDistance += dist
            val elev = location.altitude - lastLocation!!.altitude
            if (elev > 0) totalElevationGain += elev.toFloat()
        }
        lastLocation = location
        updateUI()
    }

    private fun updateUI() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        isKmh = prefs.getBoolean("isKmh", true)
        val speed = if (isKmh) currentSpeedMs * 3.6f else currentSpeedMs * 2.23694f
        tvSpeed.text = String.format(Locale.getDefault(), "%.1f", speed)
        tvSpeedUnit.text = if (isKmh) "km/h" else "mph"
        
        val factor = if (isKmh) 0.001f else 0.000621371f
        val unit = if (isKmh) "km" else "mi"
        setStatValue(viewTrip, String.format(Locale.getDefault(), "%.2f %s", tripDistance * factor, unit))
        
        val elevFactor = if (isKmh) 1f else 3.28084f
        val elevUnit = if (isKmh) "m" else "ft"
        setStatValue(viewElevation, String.format(Locale.getDefault(), "%.0f %s", totalElevationGain * elevFactor, elevUnit))

        val elapsed = getElapsedTimeSeconds()
        val avgSpeedMs = if (elapsed > 0) tripDistance / elapsed else 0f
        val avgSpeed = if (isKmh) avgSpeedMs * 3.6f else avgSpeedMs * 2.23694f
        setStatValue(viewAvgSpeed, String.format(Locale.getDefault(), "%.1f", avgSpeed))
        
        val maxSpeed = if (isKmh) maxSpeedMs * 3.6f else maxSpeedMs * 2.23694f
        setStatValue(viewMaxSpeed, String.format(Locale.getDefault(), "%.1f", maxSpeed))
    }

    private fun getElapsedTimeSeconds(): Long {
        if (startTimeMillis == 0L) return 0
        val now = if (isPaused) pauseStartTimeMillis else System.currentTimeMillis()
        return (now - startTimeMillis - totalPausedTimeMillis) / 1000
    }

    private fun updateTimeUI() {
        val seconds = getElapsedTimeSeconds()
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        setStatValue(viewTime, String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s))
        
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val weight = prefs.getString("weight", "70")?.toFloatOrNull() ?: 70f
        
        // Calories calculation: only count when moving > 2 km/h
        val speedKmh = currentSpeedMs * 3.6f
        if (speedKmh > 2.0f) {
            val met = if (speedKmh < 16) 4.0f else 8.0f
            totalCalories += met * weight * (1.0f / 3600.0f) // added for this 1 second
        }
        setStatValue(viewCalories, String.format(Locale.getDefault(), "%.0f kcal", totalCalories))
    }

    private fun pauseTracking() { isPaused = true; pauseStartTimeMillis = System.currentTimeMillis(); findViewById<Button>(R.id.btnActivePause).text = getString(R.string.resume) }
    private fun resumeTracking() { isPaused = false; totalPausedTimeMillis += System.currentTimeMillis() - pauseStartTimeMillis; findViewById<Button>(R.id.btnActivePause).text = getString(R.string.pause) }

    private fun showDetailedStats() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val odo = prefs.getFloat("totalOdometer", 0f)
        val isKmh = prefs.getBoolean("isKmh", true)
        val factor = if (isKmh) 0.001f else 0.000621371f
        val unit = if (isKmh) "km" else "mi"
        
        val msg = "ODO: ${String.format(Locale.getDefault(), "%.1f %s", (odo + tripDistance) * factor, unit)}\n" +
                  "This Session: ${String.format(Locale.getDefault(), "%.2f %s", tripDistance * factor, unit)}"
        
        AlertDialog.Builder(this).setTitle(R.string.statistics_btn).setMessage(msg).setPositiveButton("OK", null).show()
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

    private fun showStopConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_stop_title)
            .setMessage(R.string.confirm_stop_message)
            .setPositiveButton(R.string.yes) { _, _ -> 
                saveTrip()
                val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
                prefs.edit().putFloat("totalOdometer", prefs.getFloat("totalOdometer", 0f) + tripDistance).apply()
                finish() 
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun saveTrip() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val tripsJson = prefs.getString("trips_history", "[]") ?: "[]"
        try {
            val tripsArray = org.json.JSONArray(tripsJson)
            val trip = JSONObject().apply {
                put("date", System.currentTimeMillis())
                put("distance", tripDistance)
                put("maxSpeed", maxSpeedMs)
                put("elevation", totalElevationGain)
                put("calories", totalCalories)
                put("duration", getElapsedTimeSeconds())
            }
            tripsArray.put(trip)
            prefs.edit().putString("trips_history", tripsArray.toString()).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
