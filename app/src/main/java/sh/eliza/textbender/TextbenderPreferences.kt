package sh.eliza.textbender

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.preference.PreferenceManager
import java.util.concurrent.ConcurrentHashMap

private val onChangeListeners = ConcurrentHashMap<() -> Unit, OnSharedPreferenceChangeListener>()

data class TextbenderPreferences(
  val accessibilityShortcutEnabled: Boolean,
  val floatingButtonEnabled: Boolean,
  val tapDestination: Destination,
  val longPressDestination: Destination,
  val globalContextMenuDestination: Destination,
  val shareDestination: Destination,
  val urlDestination: Destination,
  val urlFormat: String,
) {
  enum class Destination {
    CLIPBOARD,
    URL,
    SHARE,
    YOMICHAN,
  }

  companion object {
    fun createFromContext(context: Context): TextbenderPreferences {
      val preferences = PreferenceManager.getDefaultSharedPreferences(context)

      val accessibilityShortcutEnabled = preferences.getBoolean("accessibility_shortcut", false)
      val floatingButtonEnabled = preferences.getBoolean("floating_button", false)

      val tapDestination = preferences.getDestination("tap_destination")
      val longPressDestination = preferences.getDestination("long_press_destination")
      val globalContextMenuDestination =
        preferences.getDestination("global_context_menu_destination")
      val shareDestination = preferences.getDestination("share_destination")
      val urlDestination = preferences.getDestination("url_destination")
      val urlFormat =
        preferences.getString("url_format", null) ?: context.getString(R.string.url_format_default)
      return TextbenderPreferences(
        accessibilityShortcutEnabled,
        floatingButtonEnabled,
        tapDestination,
        longPressDestination,
        globalContextMenuDestination,
        shareDestination,
        urlDestination,
        urlFormat
      )
    }

    fun registerOnChangeListener(context: Context, listener: () -> Unit) {
      val preferences = PreferenceManager.getDefaultSharedPreferences(context)
      val innerListener = OnSharedPreferenceChangeListener { _, _ -> listener() }
      onChangeListeners.put(listener, innerListener)
      preferences.registerOnSharedPreferenceChangeListener(innerListener)
    }

    fun unregisterOnChangeListener(context: Context, listener: () -> Unit) {
      val preferences = PreferenceManager.getDefaultSharedPreferences(context)
      onChangeListeners.remove(listener)?.let {
        preferences.unregisterOnSharedPreferenceChangeListener(it)
      }
    }

    private fun SharedPreferences.getDestination(key: String) =
      when (getString(key, "clipboard")) {
        "clipboard" -> Destination.CLIPBOARD
        "url" -> Destination.URL
        "share" -> Destination.SHARE
        "yomichan" -> Destination.YOMICHAN
        else -> throw IllegalArgumentException()
      }
  }
}
