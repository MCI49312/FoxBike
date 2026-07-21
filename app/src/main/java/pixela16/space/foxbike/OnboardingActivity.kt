package pixela16.space.foxbike

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*

class OnboardingActivity : AppCompatActivity() {

    private val langCodes = arrayOf("en", "hu", "de", "ro", "pl", "bg", "el", "sk", "cs", "sr", "hr", "zh", "ja")
    private val langDisplay = arrayOf(
        "🇺🇸 English", "🇭🇺 Magyar", "🇩🇪 Deutsch", "🇷🇴 Română", "🇵🇱 Polski", 
        "🇧🇬 Български", "🇬🇷 Ελληνικά", "🇸🇰 Slovenčina", "🇨🇿 Čeština", 
        "🇷🇸 Srpski", "🇭🇷 Hrvatski", "🇨🇳 中文", "🇯🇵 日本語"
    )

    private var userName: String = ""
    private var userWeight: String = ""
    private var vehicleType: String = ""
    private var language: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboardingFinished", false)) {
            val nextIntent = if (prefs.getBoolean("useOldUI", false)) {
                Intent(this, OldMainActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }
            startActivity(nextIntent)
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onboarding_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startWelcomeAnimation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("savedName", findViewById<EditText>(R.id.etName).text.toString())
        outState.putString("savedWeight", findViewById<EditText>(R.id.etWeight).text.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        findViewById<EditText>(R.id.etName).setText(savedInstanceState.getString("savedName", ""))
        findViewById<EditText>(R.id.etWeight).setText(savedInstanceState.getString("savedWeight", ""))
    }

    private fun startWelcomeAnimation() {
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        tvWelcome.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        tvWelcome.startAnimation(anim)

        Handler(Looper.getMainLooper()).postDelayed({
            showSetup()
        }, 2000)
    }

    private fun showSetup() {
        val cardSetup = findViewById<View>(R.id.cardSetup)
        val tvWelcome = findViewById<View>(R.id.tvWelcome)
        
        tvWelcome.visibility = View.GONE
        cardSetup.visibility = View.VISIBLE
        cardSetup.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left))

        val spinnerLang = findViewById<Spinner>(R.id.spinnerLanguage)
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langDisplay)
        spinnerLang.adapter = langAdapter
        
        val currentLang = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE).getString("language", "en") ?: "en"
        spinnerLang.setSelection(langCodes.indexOf(currentLang).coerceAtLeast(0), false)
        
        spinnerLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val newLang = langCodes[pos]
                val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
                if (newLang != prefs.getString("language", "")) {
                    prefs.edit { putString("language", newLang) }
                    updateLocale(newLang)
                    
                    // Save current inputs before recreation
                    userName = findViewById<EditText>(R.id.etName).text.toString()
                    userWeight = findViewById<EditText>(R.id.etWeight).text.toString()
                    
                    recreate()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val spinnerVehicle = findViewById<Spinner>(R.id.spinnerVehicle)
        val vehicleIds = arrayOf("bicycle", "e_scooter", "motorcycle")
        val vehicles = arrayOf(
            "🚲 " + getString(R.string.bicycle),
            "🛴 " + getString(R.string.e_scooter),
            "🏍️ " + getString(R.string.motorcycle)
        )
        val vehicleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vehicles)
        spinnerVehicle.adapter = vehicleAdapter

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            userName = findViewById<EditText>(R.id.etName).text.toString()
            userWeight = findViewById<EditText>(R.id.etWeight).text.toString()
            
            if (userName.isEmpty()) {
                Toast.makeText(this, R.string.enter_name, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (userWeight.isEmpty()) {
                Toast.makeText(this, R.string.weight, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            vehicleType = vehicleIds[spinnerVehicle.selectedItemPosition]
            language = langCodes[spinnerLang.selectedItemPosition]
            
            updateLocale(language)
            showFeatureSelection()
        }
    }

    private fun showFeatureSelection() {
        findViewById<View>(R.id.cardSetup).visibility = View.GONE
        val cardFeatures = findViewById<View>(R.id.cardFeatures)
        cardFeatures.visibility = View.VISIBLE
        cardFeatures.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left))

        findViewById<Button>(R.id.btnFinishSetup).setOnClickListener {
            val disableMaps = findViewById<CheckBox>(R.id.cbDisableMaps).isChecked
            val disableUpdater = findViewById<CheckBox>(R.id.cbDisableUpdater).isChecked

            val prefs = getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
            prefs.edit {
                putString("userName", userName)
                putString("weight", userWeight)
                putString("vehicleType", vehicleType)
                putString("language", language)
                putBoolean("disableMaps", disableMaps)
                putBoolean("disableUpdater", disableUpdater)
                putBoolean("onboardingFinished", true)
            }
            
            showReadyScreen()
        }
    }

    private fun showReadyScreen() {
        findViewById<View>(R.id.cardFeatures).visibility = View.GONE
        val tvReady = findViewById<TextView>(R.id.tvReady)
        tvReady.text = getString(R.string.ready_title)
        tvReady.visibility = View.VISIBLE
        tvReady.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 3000)
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
