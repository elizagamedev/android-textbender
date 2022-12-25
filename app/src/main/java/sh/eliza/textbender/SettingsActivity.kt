package sh.eliza.textbender

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

private const val TAG = "SettingsActivity"

private interface PermissionPreference {
  fun onResume()
}

class SettingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.settings_activity)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
    }
  }

  class SettingsFragment() : PreferenceFragmentCompat() {
    private inner class RuntimePermissionPreference(
      key: String,
      private val permission: String,
      private val settingsIntent: () -> Intent
    ) : PermissionPreference {
      private val isGranted: Boolean
        get() =
          ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED

      private val preference: SwitchPreferenceCompat =
        findPreference<SwitchPreferenceCompat>(key)!!.apply {
          setPersistent(false)

          onPreferenceClickListener = OnPreferenceClickListener {
            if (isGranted) {
              startSettingsIntent()
            } else {
              activityResultLauncher.launch(permission)
            }
            true
          }

          onPreferenceChangeListener = OnPreferenceChangeListener { _, _ -> false }
        }

      private val activityResultLauncher =
        registerForActivityResult(RequestPermission()) {
          if (!it) {
            startSettingsIntent()
          }
          preference.setChecked(it)
        }

      override fun onResume() {
        preference.setChecked(isGranted)
      }

      private fun startSettingsIntent() {
        startActivity(
          settingsIntent().apply {
            flags =
              Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
          }
        )
      }
    }

    private inner class SettingsPermissionPreference(
      key: String,
      private val isGranted: () -> Boolean,
      private val settingsIntent: () -> Intent
    ) : PermissionPreference {
      private val preference: SwitchPreferenceCompat =
        findPreference<SwitchPreferenceCompat>(key)!!.apply {
          setPersistent(false)

          onPreferenceClickListener = OnPreferenceClickListener {
            startSettingsIntent()
            true
          }

          onPreferenceChangeListener = OnPreferenceChangeListener { _, _ -> false }
        }

      override fun onResume() {
        preference.setChecked(isGranted())
      }

      private fun startSettingsIntent() {
        startActivity(
          settingsIntent().apply {
            flags =
              Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
          }
        )
      }
    }

    private lateinit var accessibilityPreference: PermissionPreference
    private lateinit var notificationsPreference: PermissionPreference
    private lateinit var fingerprintGestureOverlayPreference: Preference
    private lateinit var fingerprintGestureClipboardPreference: Preference

    private val handler = Handler(Looper.getMainLooper())
    private var isAccessibilityServiceEnabled = false
    private var isFingerprintAvailable = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
      setPreferencesFromResource(R.xml.root_preferences, rootKey)

      accessibilityPreference =
        SettingsPermissionPreference(
          "accessibility",
          { isAccessibilityServiceEnabled },
          { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        )

      val notificationsPreferenceSettingsIntent = {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
      }

      notificationsPreference =
        if (Build.VERSION.SDK_INT >= 33) {
          RuntimePermissionPreference(
            "notifications",
            Manifest.permission.POST_NOTIFICATIONS,
            notificationsPreferenceSettingsIntent
          )
        } else {
          SettingsPermissionPreference(
            "notifications",
            { NotificationManagerCompat.from(requireContext()).areNotificationsEnabled() },
            notificationsPreferenceSettingsIntent
          )
        }

      findPreference<ListPreference>("global_context_menu_destination")!!.setAsComponentDestination(
        "ContextMenuAction"
      )
      findPreference<ListPreference>("share_destination")!!.setAsComponentDestination("ShareAction")
      findPreference<ListPreference>("url_destination")!!.setAsComponentDestination("UrlAction")

      findPreference<EditTextPreference>("url_format")!!.setOnBindEditTextListener {
        it.hint = getString(R.string.url_format_default)
      }

      findPreference<EditTextPreference>("strip_regexp")!!.setOnBindEditTextListener {
        it.hint = "\\s+"
      }

      fingerprintGestureOverlayPreference =
        findPreference<Preference>("fingerprint_gesture_overlay")!!
      fingerprintGestureClipboardPreference =
        findPreference<Preference>("fingerprint_gesture_clipboard")!!

      TextbenderService.addOnFingerprintAvailableListener(this::onFingerprintAvailable, handler)
    }

    override fun onResume() {
      super.onResume()
      isAccessibilityServiceEnabled =
        try {
          val context = requireContext()
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

      accessibilityPreference.onResume()
      notificationsPreference.onResume()

      recalculateDependencies()
    }

    override fun onDestroy() {
      super.onDestroy()
      TextbenderService.removeOnFingerprintAvailableListener(this::onFingerprintAvailable)
    }

    private fun onFingerprintAvailable() {
      isFingerprintAvailable = true
      recalculateDependencies()
    }

    private fun recalculateDependencies() {
      val enabled = isFingerprintAvailable && isAccessibilityServiceEnabled
      fingerprintGestureOverlayPreference.setEnabled(enabled)
      fingerprintGestureClipboardPreference.setEnabled(enabled)
    }

    private fun ListPreference.setAsComponentDestination(className: String) {
      val context = requireContext()
      val componentName = ComponentName(context, "${context.packageName}.$className")
      onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
        val enabled = newValue as String != "disabled"
        requireContext()
          .packageManager
          .setComponentEnabledSetting(
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
