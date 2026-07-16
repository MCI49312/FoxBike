package pixela16.space.foxbike

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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

        val spinnerLanguage = findViewById<Spinner>(R.id.spinnerLanguage)
        val languages = arrayOf(
            "English", "Magyar", "Deutsch", "Română", "Polski", 
            "Български", "Ελληνικά", "Slovenčina", "Čeština", 
            "Srpski", "Hrvatski", "中文", "日本語"
        )
        val langCodes = arrayOf(
            "en", "hu", "de", "ro", "pl", 
            "bg", "el", "sk", "cs", 
            "sr", "hr", "zh", "ja"
        )
        
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinnerLanguage.adapter = langAdapter
        
        val langIndex = langCodes.indexOf(currentLang).coerceAtLeast(0)
        spinnerLanguage.setSelection(langIndex)

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newLang = langCodes[position]
                if (newLang != prefs.getString("language", "en")) {
                    prefs.edit { putString("language", newLang) }
                    updateLocale(newLang)
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val spinnerUnits = findViewById<Spinner>(R.id.spinnerUnits)
        val units = arrayOf("km/h", "mph")
        val unitsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
        spinnerUnits.adapter = unitsAdapter
        
        val isKmh = prefs.getBoolean("isKmh", true)
        spinnerUnits.setSelection(if (isKmh) 0 else 1)

        spinnerUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit { putBoolean("isKmh", position == 0) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<CheckBox>(R.id.cbUseMaps).apply {
            isChecked = prefs.getBoolean("useMaps", true)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit { putBoolean("useMaps", isChecked) } }
        }

        findViewById<CheckBox>(R.id.cbVoiceFeedback).apply {
            isChecked = prefs.getBoolean("voiceFeedback", false)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit { putBoolean("voiceFeedback", isChecked) } }
        }

        findViewById<CheckBox>(R.id.cbServiceReminder).apply {
            isChecked = prefs.getBoolean("serviceReminder", true)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit { putBoolean("serviceReminder", isChecked) } }
        }

        findViewById<Button>(R.id.btnServiceSettings).setOnClickListener {
            startActivity(android.content.Intent(this, ServiceReminderActivity::class.java))
        }

        findViewById<CheckBox>(R.id.cbDarkMode).apply {
            isChecked = prefs.getBoolean("darkModeManual", false)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("darkModeManual", isChecked) }
                AppCompatDelegate.setDefaultNightMode(if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        val etWeatherLocation = findViewById<AutoCompleteTextView>(R.id.etWeatherLocation)
        etWeatherLocation.setText(prefs.getString("weatherLocation", "Budapest"))
        
        val cityAdapter = CityAutocompleteAdapter(this)
        etWeatherLocation.setAdapter(cityAdapter)
        
        etWeatherLocation.addTextChangedListener {
            prefs.edit { putString("weatherLocation", it.toString()) }
        }

        val spinnerWindUnits = findViewById<Spinner>(R.id.spinnerWindUnits)
        val windUnits = arrayOf("km/h", "mph", "m/s")
        val windUnitsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, windUnits)
        spinnerWindUnits.adapter = windUnitsAdapter
        
        val currentWindUnit = prefs.getString("windUnit", "km/h")
        spinnerWindUnits.setSelection(windUnits.indexOf(currentWindUnit).coerceAtLeast(0))

        spinnerWindUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit { putString("windUnit", windUnits[position]) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val etWeight = findViewById<EditText>(R.id.etWeight)
        etWeight.setText(prefs.getString("weight", "70"))
        etWeight.addTextChangedListener { prefs.edit { putString("weight", it.toString()) } }

        findViewById<Button>(R.id.btnResetOdometer).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_odometer)
                .setMessage(R.string.confirm_reset_odometer)
                .setPositiveButton(R.string.yes) { _, _ ->
                    prefs.edit { putFloat("totalOdometer", 0f) }
                    Toast.makeText(this, R.string.reset_odometer, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        val layoutDemoMode = findViewById<LinearLayout>(R.id.layoutDemoMode)
        layoutDemoMode.visibility = if (prefs.getBoolean("demoMode", false)) View.VISIBLE else View.GONE

        val etFakeSpeed = findViewById<EditText>(R.id.etFakeSpeed)
        val etFakeOdometer = findViewById<EditText>(R.id.etFakeOdometer)
        val etFakeTrip = findViewById<EditText>(R.id.etFakeTrip)
        
        etFakeSpeed.setText(prefs.getFloat("fakeSpeed", 0f).toString())
        etFakeOdometer.setText(prefs.getFloat("fakeOdometer", 0f).toString())
        etFakeTrip.setText(prefs.getFloat("fakeTrip", 0f).toString())

        findViewById<Button>(R.id.btnSaveDemo).setOnClickListener {
            prefs.edit {
                putFloat("fakeSpeed", etFakeSpeed.text.toString().toFloatOrNull() ?: 0f)
                putFloat("fakeOdometer", etFakeOdometer.text.toString().toFloatOrNull() ?: 0f)
                putFloat("fakeTrip", etFakeTrip.text.toString().toFloatOrNull() ?: 0f)
            }
            Toast.makeText(this, "Demo values saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvVersion).setOnClickListener {
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
                if (code == "1900") {
                    prefs.edit { putBoolean("demoMode", true) }
                    Toast.makeText(this, R.string.demo_mode_enabled, Toast.LENGTH_SHORT).show()
                    findViewById<LinearLayout>(R.id.layoutDemoMode).visibility = View.VISIBLE
                } else if (code == "1901") {
                    prefs.edit { putBoolean("demoMode", false) }
                    Toast.makeText(this, R.string.demo_mode_disabled, Toast.LENGTH_SHORT).show()
                    findViewById<LinearLayout>(R.id.layoutDemoMode).visibility = View.GONE
                }
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
