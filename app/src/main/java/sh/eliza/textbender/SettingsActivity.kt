package sh.eliza.textbender

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

private const val TAG = "SettingsActivity"

class SettingsActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.settings_activity)

    val globalContextMenuComponentName = ComponentName(this, "${packageName}.ContextMenuAction")
    val shareComponentName = ComponentName(this, "${packageName}.ShareAction")
    val urlComponentName = ComponentName(this, "${packageName}.UrlAction")

    if (savedInstanceState == null) {
      supportFragmentManager
        .beginTransaction()
        .replace(
          R.id.settings,
          SettingsFragment(
            packageManager,
            globalContextMenuComponentName,
            shareComponentName,
            urlComponentName
          )
        )
        .commit()
    }
  }

  class SettingsFragment(
    private val packageManager: PackageManager,
    private val globalContextMenuComponentName: ComponentName,
    private val shareComponentName: ComponentName,
    private val urlComponentName: ComponentName,
  ) : PreferenceFragmentCompat() {
    private lateinit var accessibilityPreference: SwitchPreferenceCompat

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

      findPreference<ListPreference>("global_context_menu_destination")!!.setAsComponentDestination(
        globalContextMenuComponentName
      )
      findPreference<ListPreference>("share_destination")!!.setAsComponentDestination(
        shareComponentName
      )
      findPreference<ListPreference>("url_destination")!!.setAsComponentDestination(
        urlComponentName
      )

      findPreference<EditTextPreference>("url_format")!!.setOnBindEditTextListener {
        it.hint = getString(R.string.url_format_default)
      }
    }

    override fun onResume() {
      super.onResume()

      val context = requireContext()

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
              .contains("${context.packageName}/${TextbenderService::class.qualifiedName}")
        } catch (e: Settings.SettingNotFoundException) {
          Log.e(TAG, "Failed to determine if accessibility service enabled", e)
          false
        }
      )
    }

    private fun ListPreference.setAsComponentDestination(componentName: ComponentName) {
      onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
        val enabled = newValue as String != "disabled"
        packageManager.setComponentEnabledSetting(
          componentName,
          if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
          } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
          },
          PackageManager.DONT_KILL_APP
        )

        true
      }
    }
  }
}
