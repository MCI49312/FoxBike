package pixela16.space.foxbike

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class SettingsActivity : AppCompatActivity() {

    private var clickCount = 0
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val currentLang = prefs.getString("language", "en") ?: "en"
        updateLocale(currentLang)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Language Spinner
        val spinnerLanguage = findViewById<Spinner>(R.id.spinnerLanguage)
        val languages = arrayOf(
            "🇺🇸 English", "🇭🇺 Magyar", "🇩🇪 Deutsch", "🇷🇴 Română", "🇵🇱 Polski", 
            "🇧🇬 Български", "🇬🇷 Ελληνικά", "🇸🇰 Slovenčina", "🇨🇿 Čeština", 
            "🇷🇸 Srpski", "🇭🇷 Hrvatski", "🇨🇳 中文", "🇯🇵 日本語"
        )
        val langCodes = arrayOf("en", "hu", "de", "ro", "pl", "bg", "el", "sk", "cs", "sr", "hr", "zh", "ja")
        spinnerLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinnerLanguage.setSelection(langCodes.indexOf(currentLang).coerceAtLeast(0))
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val newLang = langCodes[pos]
                if (newLang != prefs.getString("language", "en")) {
                    prefs.edit { putString("language", newLang) }
                    updateLocale(newLang)
                    recreate()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Vehicle Spinner
        val spinnerVehicle = findViewById<Spinner>(R.id.spinnerSettingsVehicle)
        val vehicleIds = arrayOf("bicycle", "e_scooter", "motorcycle")
        val vehicles = arrayOf(getString(R.string.bicycle), getString(R.string.e_scooter), getString(R.string.motorcycle))
        spinnerVehicle.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vehicles)
        spinnerVehicle.setSelection(vehicleIds.indexOf(prefs.getString("vehicleType", "bicycle")).coerceAtLeast(0))
        spinnerVehicle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit { putString("vehicleType", vehicleIds[pos]) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Units Spinners
        val spinnerTemp = findViewById<Spinner>(R.id.spinnerTempUnit)
        val tempUnits = arrayOf("°C", "°F")
        spinnerTemp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tempUnits)
        spinnerTemp.setSelection(if (prefs.getString("tempUnit", "°C") == "°C") 0 else 1)
        spinnerTemp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit { putString("tempUnit", tempUnits[pos]) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val spinnerWind = findViewById<Spinner>(R.id.spinnerWindUnit)
        val windUnits = arrayOf("km/h", "mph", "m/s")
        spinnerWind.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, windUnits)
        spinnerWind.setSelection(windUnits.indexOf(prefs.getString("windUnit", "km/h")).coerceAtLeast(0))
        spinnerWind.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit { putString("windUnit", windUnits[pos]) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val spinnerDist = findViewById<Spinner>(R.id.spinnerDistanceUnit)
        val distUnits = arrayOf("Metric (km)", "Imperial (mi)")
        spinnerDist.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, distUnits)
        spinnerDist.setSelection(if (prefs.getBoolean("isKmh", true)) 0 else 1)
        spinnerDist.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit { putBoolean("isKmh", pos == 0) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        findViewById<EditText>(R.id.etSettingsName).apply {
            setText(prefs.getString("userName", ""))
            addTextChangedListener { prefs.edit { putString("userName", it.toString()) } }
        }

        findViewById<EditText>(R.id.etSettingsWeight).apply {
            setText(prefs.getString("weight", "70"))
            addTextChangedListener { prefs.edit { putString("weight", it.toString()) } }
        }

        val btnSwitchUI = findViewById<Button>(R.id.btnSwitchUI)
        val useOld = prefs.getBoolean("useOldUI", false)
        btnSwitchUI.text = if (useOld) getString(R.string.use_new_ui) else getString(R.string.use_old_ui)
        btnSwitchUI.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_ui_switch)
                .setPositiveButton(R.string.yes) { _, _ ->
                    prefs.edit { putBoolean("useOldUI", !useOld) }
                    val nextIntent = if (!useOld) Intent(this, OldMainActivity::class.java) else Intent(this, MainActivity::class.java)
                    startActivity(nextIntent)
                    finishAffinity()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        findViewById<Button>(R.id.btnResetOdo).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_odometer)
                .setMessage(R.string.confirm_reset_odometer)
                .setPositiveButton(R.string.yes) { _, _ -> prefs.edit { putFloat("totalOdometer", 0f) } }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        findViewById<Button>(R.id.btnFullReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.full_app_reset)
                .setMessage(R.string.confirm_full_reset)
                .setPositiveButton(R.string.yes) { _, _ ->
                    prefs.edit { clear() }
                    startActivity(Intent(this, OnboardingActivity::class.java))
                    finishAffinity()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        val tvVersion = findViewById<TextView>(R.id.tvSettingsVersion)
        tvVersion.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) clickCount++ else clickCount = 1
            lastClickTime = currentTime
            if (clickCount == 7) {
                showCodeDialog()
                clickCount = 0
            }
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
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class CityAutocompleteAdapter(context: Context) : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line), Filterable {
        private var results: List<String> = arrayListOf()

        override fun getCount(): Int = results.size
        override fun getItem(index: Int): String? = if (index < results.size) results[index] else null

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val filterResults = FilterResults()
                    if (constraint != null) {
                        results = fetchCitySuggestions(constraint.toString())
                        filterResults.values = results
                        filterResults.count = results.size
                    }
                    return filterResults
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged()
                    } else {
                        notifyDataSetInvalidated()
                    }
                }
            }
        }

        private fun fetchCitySuggestions(query: String): List<String> {
            val list = mutableListOf<String>()
            try {
                val url = URL("https://nominatim.openstreetmap.org/search?q=$query&format=json&addressdetails=1&limit=5")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "FoxBike-Android-App")
                val response = conn.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(obj.getString("display_name"))
                }
            } catch (e: Exception) {}
            return list
        }
    }
}
