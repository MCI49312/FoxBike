package pixela16.space.foxbike

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.content.Intent
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private var currentLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        
        if (prefs.getBoolean("useOldUI", false)) {
            startActivity(Intent(this, OldMainActivity::class.java))
            finish()
            return
        }

        val lang = prefs.getString("language", "en") ?: "en"
        currentLanguage = lang
        updateLocale(lang)

        if (prefs.getBoolean("darkModeManual", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNavigation)

        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> viewPager.currentItem = 0
                R.id.nav_settings -> viewPager.currentItem = 1
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.menu.getItem(position).isChecked = true
            }
        })
    }

    private fun updateLocale(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                else -> SettingsFragment()
            }
        }
    }

    class HomeFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = inflater.inflate(R.layout.fragment_home, container, false)
            val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
            
            updateHomeUI(view, prefs)
            
            val cardMap = view.findViewById<View>(R.id.cardMap)
            if (prefs.getBoolean("disableMaps", false)) {
                cardMap.visibility = View.GONE
            } else {
                cardMap.setOnClickListener {
                    startActivity(Intent(requireContext(), MapActivity::class.java))
                }
            }

            view.findViewById<View>(R.id.cardStats).setOnClickListener {
                showSummaryStats()
            }

            view.findViewById<View>(R.id.cardTrips).setOnClickListener {
                showTripsHistory()
            }

            view.findViewById<Button>(R.id.btnStartTrip).setOnClickListener {
                startActivity(Intent(requireContext(), ActiveTripActivity::class.java))
            }

            return view
        }

        private fun updateHomeUI(view: View, prefs: android.content.SharedPreferences) {
            val name = prefs.getString("userName", "User")
            val vehicleId = prefs.getString("vehicleType", "bicycle")
            
            view.findViewById<TextView>(R.id.tvHiName).text = getString(R.string.hi_name, name)
            
            val vehicleString = when(vehicleId) {
                "e_scooter" -> getString(R.string.e_scooter_ready)
                "motorcycle" -> getString(R.string.motorcycle_ready)
                else -> getString(R.string.bicycle_ready)
            }
            
            view.findViewById<TextView>(R.id.tvReadyRide).text = getString(R.string.ready_to_ride, vehicleString)
            
            val isKmh = prefs.getBoolean("isKmh", true)
            val totalOdo = prefs.getFloat("totalOdometer", 0f)
            val factor = if (isKmh) 0.001f else 0.000621371f
            val unit = if (isKmh) "km" else "mi"
            view.findViewById<TextView>(R.id.tvHomeOdo).text = String.format(Locale.getDefault(), "ODO: %.1f %s", totalOdo * factor, unit)
        }

        private fun showSummaryStats() {
            val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
            val isKmh = prefs.getBoolean("isKmh", true)
            val odo = prefs.getFloat("totalOdometer", 0f)
            val factor = if (isKmh) 0.001f else 0.000621371f
            val unit = if (isKmh) "km" else "mi"
            
            val msg = String.format(Locale.getDefault(), "ODO: %.1f %s\nThis Session: 0.00 %s", odo * factor, unit, unit)
            
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.trip_stats)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }

        private fun showTripsHistory() {
            val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
            val tripsJson = prefs.getString("trips_history", "[]") ?: "[]"
            val tripsArray = org.json.JSONArray(tripsJson)
            
            if (tripsArray.length() == 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.trips)
                    .setMessage("No saved trips yet.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            val adapter = TripHistoryAdapter(requireContext(), tripsArray, 
                onDelete = { index ->
                    val newArray = org.json.JSONArray()
                    for (i in 0 until tripsArray.length()) {
                        if (i != index) newArray.put(tripsArray.get(i))
                    }
                    prefs.edit().putString("trips_history", newArray.toString()).apply()
                    showTripsHistory() // Refresh the dialog (by showing a new one, simple for now)
                },
                onClick = { index ->
                    showTripDetails(tripsArray.getJSONObject(index))
                }
            )

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.trips)
                .setAdapter(adapter, null)
                .setPositiveButton(R.string.cancel, null)
                .show()
        }

        private fun showTripDetails(trip: org.json.JSONObject) {
            val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
            val isKmh = prefs.getBoolean("isKmh", true)
            val factor = if (isKmh) 0.001f else 0.000621371f
            val speedFactor = if (isKmh) 3.6f else 2.23694f
            val unit = if (isKmh) "km" else "mi"
            val speedUnit = if (isKmh) "km/h" else "mph"
            
            val dist = trip.getDouble("distance").toFloat()
            val maxSpeed = trip.getDouble("maxSpeed").toFloat()
            val duration = trip.getLong("duration")
            val calories = trip.optDouble("calories", 0.0).toFloat()
            
            val h = duration / 3600
            val m = (duration % 3600) / 60
            val s = duration % 60
            val timeStr = String.format("%02d:%02d:%02d", h, m, s)

            val avgSpeedMs = if (duration > 0) dist / duration else 0f

            val msg = "Distance: ${String.format("%.2f %s", dist * factor, unit)}\n" +
                      "Time: $timeStr\n" +
                      "Avg Speed: ${String.format("%.1f %s", avgSpeedMs * speedFactor, speedUnit)}\n" +
                      "Max Speed: ${String.format("%.1f %s", maxSpeed * speedFactor, speedUnit)}\n" +
                      "Calories: ${String.format("%.0f kcal", calories)}"

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.trip_stats)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }

        override fun onResume() {
            super.onResume()
            val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
            view?.let { updateHomeUI(it, prefs) }
        }
    }

    class SettingsFragment : Fragment() {
        private var clickCount = 0
        private var lastClickTime = 0L

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = inflater.inflate(R.layout.fragment_settings, container, false)
            val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
            
            // Language Spinner
            val spinnerLang = view.findViewById<Spinner>(R.id.spinnerSettingsLanguage)
            val langCodes = arrayOf("en", "hu", "de", "ro", "pl", "bg", "el", "sk", "cs", "sr", "hr", "zh", "ja")
            val langDisplay = arrayOf(
                "🇺🇸 English", "🇭🇺 Magyar", "🇩🇪 Deutsch", "🇷🇴 Română", "🇵🇱 Polski", 
                "🇧🇬 Български", "🇬🇷 Ελληνικά", "🇸🇰 Slovenčina", "🇨🇿 Čeština", 
                "🇷🇸 Srpski", "🇭🇷 Hrvatski", "🇨🇳 中文", "🇯🇵 日本語"
            )
            spinnerLang.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, langDisplay)
            val currentLang = prefs.getString("language", "en") ?: "en"
            spinnerLang.setSelection(langCodes.indexOf(currentLang).coerceAtLeast(0))
            spinnerLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    val newLang = langCodes[pos]
                    if (newLang != prefs.getString("language", "en")) {
                        prefs.edit { putString("language", newLang) }
                        (requireActivity() as MainActivity).updateLocale(newLang)
                        requireActivity().recreate()
                    }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            val etName = view.findViewById<EditText>(R.id.etSettingsName)
            etName.setText(prefs.getString("userName", ""))
            etName.addTextChangedListener { prefs.edit { putString("userName", it.toString()) } }

            val spinnerVehicle = view.findViewById<Spinner>(R.id.spinnerSettingsVehicle)
            val vehicleIds = arrayOf("bicycle", "e_scooter", "motorcycle")
            val vehicles = arrayOf(getString(R.string.bicycle), getString(R.string.e_scooter), getString(R.string.motorcycle))
            val vehicleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, vehicles)
            spinnerVehicle.adapter = vehicleAdapter
            val currentVehicleId = prefs.getString("vehicleType", "bicycle")
            spinnerVehicle.setSelection(vehicleIds.indexOf(currentVehicleId).coerceAtLeast(0))
            spinnerVehicle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    prefs.edit { putString("vehicleType", vehicleIds[pos]) }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            // Units Spinners
            val spinnerTemp = view.findViewById<Spinner>(R.id.spinnerTempUnit)
            val tempUnits = arrayOf("°C", "°F")
            spinnerTemp.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tempUnits)
            spinnerTemp.setSelection(if (prefs.getString("tempUnit", "°C") == "°C") 0 else 1)
            spinnerTemp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    prefs.edit { putString("tempUnit", tempUnits[pos]) }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            val spinnerWind = view.findViewById<Spinner>(R.id.spinnerWindUnit)
            val windUnits = arrayOf("km/h", "mph", "m/s")
            spinnerWind.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, windUnits)
            spinnerWind.setSelection(windUnits.indexOf(prefs.getString("windUnit", "km/h")).coerceAtLeast(0))
            spinnerWind.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    prefs.edit { putString("windUnit", windUnits[pos]) }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            val spinnerDist = view.findViewById<Spinner>(R.id.spinnerDistanceUnit)
            val distUnits = arrayOf("Metric (km)", "Imperial (mi)")
            spinnerDist.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, distUnits)
            spinnerDist.setSelection(if (prefs.getBoolean("isKmh", true)) 0 else 1)
            spinnerDist.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    prefs.edit { putBoolean("isKmh", pos == 0) }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            val etWeatherLoc = view.findViewById<AutoCompleteTextView>(R.id.etSettingsWeatherLoc)
            etWeatherLoc.setText(prefs.getString("weatherLocation", "Budapest"))
            val cityAdapter = CityAutocompleteAdapter(requireContext())
            etWeatherLoc.setAdapter(cityAdapter)
            etWeatherLoc.addTextChangedListener { prefs.edit { putString("weatherLocation", it.toString()) } }

            val etWeight = view.findViewById<EditText>(R.id.etSettingsWeight)
            etWeight.setText(prefs.getString("weight", "70"))
            etWeight.addTextChangedListener { prefs.edit { putString("weight", it.toString()) } }

            val btnSwitchUI = view.findViewById<Button>(R.id.btnSwitchUI)
            val useOld = prefs.getBoolean("useOldUI", false)
            btnSwitchUI.text = if (useOld) getString(R.string.use_new_ui) else getString(R.string.use_old_ui)
            btnSwitchUI.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.confirm_ui_switch)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        prefs.edit { putBoolean("useOldUI", !useOld) }
                        requireActivity().recreate()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }

            view.findViewById<Button>(R.id.btnResetOdo).setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.reset_odometer)
                    .setMessage(R.string.confirm_reset_odometer)
                    .setPositiveButton(R.string.yes) { _, _ -> prefs.edit { putFloat("totalOdometer", 0f) } }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }

            view.findViewById<Button>(R.id.btnFullReset).setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.full_app_reset)
                    .setMessage(R.string.confirm_full_reset)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        prefs.edit { clear() }
                        startActivity(Intent(requireContext(), OnboardingActivity::class.java))
                        requireActivity().finish()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }

            // Updater
            val layoutUpdater = view.findViewById<View>(R.id.layoutUpdater)
            if (prefs.getBoolean("disableUpdater", false)) {
                layoutUpdater.visibility = View.GONE
            } else {
                val tvBranch = view.findViewById<TextView>(R.id.tvUpdaterBranch)
                val isDev = prefs.getBoolean("useDeveloperBranch", false)
                tvBranch.text = if (isDev) "Branch: developer" else "Branch: main"
                
                view.findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
                    checkUpdates(isDev)
                }
            }

            val tvVersion = view.findViewById<TextView>(R.id.tvSettingsVersion)
            tvVersion.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 500) clickCount++ else clickCount = 1
                lastClickTime = currentTime
                if (clickCount == 7) {
                    showCodeDialog()
                    clickCount = 0
                }
            }
            
            return view
        }

        private fun checkUpdates(devBranch: Boolean) {
            val currentVersionName = "3.0 Beta 3"
            thread {
                try {
                    val url = URL("https://api.github.com/repos/MCI49312/FoxBike/releases" + if (devBranch) "" else "/latest")
                    val conn = url.openConnection() as HttpURLConnection
                    val responseText = conn.inputStream.bufferedReader().readText()
                    
                    val latestVersionName: String
                    val downloadUrl: String
                    
                    if (devBranch) {
                        val releases = JSONArray(responseText)
                        val latest = releases.getJSONObject(0)
                        latestVersionName = latest.getString("tag_name")
                        downloadUrl = latest.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
                    } else {
                        val latest = JSONObject(responseText)
                        latestVersionName = latest.getString("tag_name")
                        downloadUrl = latest.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
                    }

                    activity?.runOnUiThread {
                        if (latestVersionName != currentVersionName) {
                            AlertDialog.Builder(requireContext())
                                .setTitle(R.string.update_available)
                                .setMessage(String.format("Version %s is available.", latestVersionName))
                                .setPositiveButton(R.string.install_update) { _, _ ->
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
                                    startActivity(intent)
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                        } else {
                            Toast.makeText(requireContext(), R.string.no_update, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread { Toast.makeText(requireContext(), "Update check failed", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        private fun showCodeDialog() {
            val input = EditText(requireContext())
            input.inputType = InputType.TYPE_CLASS_NUMBER
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.enter_code)
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val code = input.text.toString()
                    val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
                    when (code) {
                        "1900" -> {
                            prefs.edit { putBoolean("demoMode", true) }
                            Toast.makeText(requireContext(), R.string.demo_mode_enabled, Toast.LENGTH_SHORT).show()
                        }
                        "1901" -> {
                            prefs.edit { putBoolean("demoMode", false) }
                            Toast.makeText(requireContext(), R.string.demo_mode_disabled, Toast.LENGTH_SHORT).show()
                        }
                        "1902" -> {
                            showSetOdometerDialog()
                        }
                        "1903" -> {
                            prefs.edit { putFloat("totalOdometer", 0f) }
                            Toast.makeText(requireContext(), R.string.reset_odometer, Toast.LENGTH_SHORT).show()
                        }
                        "1904" -> {
                            val isDev = !prefs.getBoolean("useDeveloperBranch", false)
                            prefs.edit { putBoolean("useDeveloperBranch", isDev) }
                            Toast.makeText(requireContext(), getString(R.string.branch_switched, if (isDev) "developer" else "main"), Toast.LENGTH_SHORT).show()
                            requireActivity().recreate()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun showSetOdometerDialog() {
            val input = EditText(requireContext())
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            input.hint = "Value in km"
            AlertDialog.Builder(requireContext())
                .setTitle("Set Odometer")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val value = input.text.toString().toFloatOrNull() ?: 0f
                    val prefs = requireContext().getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
                    prefs.edit { putFloat("totalOdometer", value * 1000f) }
                    Toast.makeText(requireContext(), "Odometer set to $value km", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
