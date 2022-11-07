package sh.eliza.textbender

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

private const val TAG = "SettingsActivity"

class SettingsActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.settings_activity)
    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
    }
  }

  class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var accessibilityPreference: SwitchPreferenceCompat
    private lateinit var drawOverlaysPreference: SwitchPreferenceCompat

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
      setPreferencesFromResource(R.xml.root_preferences, rootKey)

      accessibilityPreference =
        findPreference<SwitchPreferenceCompat>("accessibility")!!.apply {
          setPersistent(false)

          onPreferenceClickListener = OnPreferenceClickListener {
            val intent =
              Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
              }
            startActivity(intent)
            true
          }

          onPreferenceChangeListener = OnPreferenceChangeListener { _, _ -> false }
        }

      drawOverlaysPreference =
        findPreference<SwitchPreferenceCompat>("draw_overlays")!!.apply {
          setPersistent(false)

          onPreferenceClickListener = OnPreferenceClickListener {
            val intent =
              Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
              }
            startActivity(intent)
            true
          }

          onPreferenceChangeListener = OnPreferenceChangeListener { _, _ -> false }
        }
    }

    override fun onResume() {
      super.onResume()

      val context = context!!

      accessibilityPreference.setChecked(
        try {
          val services =
            Settings.Secure.getString(
              context.contentResolver,
              Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
          services != null &&
            services
              .split(':')
              .contains(
                "${context.packageName}/${TextbenderAccessibilityService::class.qualifiedName}"
              )
        } catch (e: Settings.SettingNotFoundException) {
          Log.e(TAG, "Failed to determine if accessibility service enabled", e)
          false
        }
      )

      drawOverlaysPreference.setChecked(
        try {
          Settings.canDrawOverlays(context)
        } catch (e: Settings.SettingNotFoundException) {
          Log.e(TAG, "Failed to determine if draw overlays permission enabled", e)
          false
        }
      )
    }
  }
}
