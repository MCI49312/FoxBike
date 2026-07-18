package pixela16.space.foxbike

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*
import android.content.Intent
import android.text.InputType

class OldMainActivity : AppCompatActivity() {

    private var clickCount = 0
    private var lastClickTime = 0L

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
        updateUI()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnMap).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnStats).setOnClickListener {
            showSummaryStats()
        }

        findViewById<ImageButton>(R.id.btnHistory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.trips)
                .setMessage("No saved trips yet.")
                .setPositiveButton("OK", null)
                .show()
        }

        findViewById<TextView>(R.id.tvOdometer).setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 500) clickCount++ else clickCount = 1
            lastClickTime = currentTime
            if (clickCount == 7) {
                showCodeDialog()
                clickCount = 0
            }
        }
    }

    private fun showSummaryStats() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val isKmh = prefs.getBoolean("isKmh", true)
        val odo = prefs.getFloat("totalOdometer", 0f)
        val factor = if (isKmh) 0.001f else 0.000621371f
        val unit = if (isKmh) "km" else "mi"
        
        val msg = String.format(Locale.getDefault(), "Total Odometer: %.1f %s", odo * factor, unit)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.statistics)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateUI() {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val isKmh = prefs.getBoolean("isKmh", true)
        val totalOdo = prefs.getFloat("totalOdometer", 0f)
        val factor = if (isKmh) 0.001f else 0.000621371f
        val unit = if (isKmh) "km" else "mi"
        
        findViewById<TextView>(R.id.tvOdometer).text = String.format(Locale.getDefault(), "ODO: %.1f %s", totalOdo * factor, unit)
        findViewById<TextView>(R.id.tvSpeedUnit).text = if (isKmh) getString(R.string.unit_kmh) else getString(R.string.unit_mph)
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
                        prefs.edit().putBoolean("demoMode", true).apply()
                        Toast.makeText(this, R.string.demo_mode_enabled, Toast.LENGTH_SHORT).show()
                    }
                    "1901" -> {
                        prefs.edit().putBoolean("demoMode", false).apply()
                        Toast.makeText(this, R.string.demo_mode_disabled, Toast.LENGTH_SHORT).show()
                    }
                    "1902" -> {
                        showSetOdometerDialog()
                    }
                    "1903" -> {
                        prefs.edit().putFloat("totalOdometer", 0f).apply()
                        Toast.makeText(this, R.string.reset_odometer, Toast.LENGTH_SHORT).show()
                        updateUI()
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
                prefs.edit().putFloat("totalOdometer", value * 1000f).apply()
                Toast.makeText(this, "Odometer set to $value km", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
