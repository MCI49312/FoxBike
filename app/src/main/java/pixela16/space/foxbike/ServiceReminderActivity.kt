package pixela16.space.foxbike

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*

class ServiceReminderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val currentLang = prefs.getString("language", "en") ?: "en"
        updateLocale(currentLang)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_service_reminder)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.service_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val etInterval = findViewById<EditText>(R.id.etInterval)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSave = findViewById<Button>(R.id.btnSave)

        etInterval.setText(prefs.getInt("serviceIntervalKm", 500).toString())
        etMessage.setText(prefs.getString("serviceMessage", getString(R.string.oil_chain_reminder)))

        btnSave.setOnClickListener {
            val interval = etInterval.text.toString().toIntOrNull() ?: 500
            val message = etMessage.text.toString()
            
            prefs.edit {
                putInt("serviceIntervalKm", interval)
                putString("serviceMessage", message)
            }
            Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show()
            finish()
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
}
