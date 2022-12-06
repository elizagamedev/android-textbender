package sh.eliza.textbender

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.preference.PreferenceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val instanceLock = ReentrantLock()
private var instance: TextbenderPreferences? = null

class TextbenderPreferences
private constructor(
  private val preferences: SharedPreferences,
  private val defaultUrlFormat: String,
) {
  enum class Destination {
    DISABLED,
    CLIPBOARD,
    URL,
    SHARE,
    YOMICHAN,
  }

  data class Snapshot(
    val floatingButtonsEnabled: Boolean,
    val floatingButtonOverlayEnabled: Boolean,
    val floatingButtonClipboardEnabled: Boolean,
    val tapDestination: Destination,
    val longPressDestination: Destination,
    val globalContextMenuDestination: Destination,
    val shareDestination: Destination,
    val urlDestination: Destination,
    val clipboardDestination: Destination,
    val urlFormat: String,
    // Hidden preferences.
    val floatingButtonsX: Int,
    val floatingButtonsY: Int,
  )

  private val onChangeListeners = ConcurrentHashMap<() -> Unit, OnSharedPreferenceChangeListener>()

  val snapshot: Snapshot
    get() {
      // UI Options
      val floatingButtonsEnabled = preferences.getBoolean("floating_buttons", false)
      val floatingButtonOverlayEnabled = preferences.getBoolean("floating_button_overlay", false)
      val floatingButtonClipboardEnabled =
        preferences.getBoolean("floating_button_clipboard", false)

      // Mappings
      val tapDestination = preferences.getDestination("tap_destination")
      val longPressDestination = preferences.getDestination("long_press_destination")
      val globalContextMenuDestination =
        preferences.getDestination("global_context_menu_destination")
      val shareDestination = preferences.getDestination("share_destination")
      if (shareDestination === Destination.SHARE) {
        throw IllegalArgumentException()
      }
      val urlDestination = preferences.getDestination("url_destination")
      val clipboardDestination = preferences.getDestination("clipboard_destination")
      if (clipboardDestination === Destination.CLIPBOARD) {
        throw IllegalArgumentException()
      }

      // Destionation Options
      val urlFormat = preferences.getString("url_format", null) ?: defaultUrlFormat

      // Hidden Options
      val floatingButtonsX = preferences.getInt("floating_buttons_x", 0)
      val floatingButtonsY = preferences.getInt("floating_buttons_y", 0)

      return Snapshot(
        floatingButtonsEnabled,
        floatingButtonOverlayEnabled,
        floatingButtonClipboardEnabled,
        tapDestination,
        longPressDestination,
        globalContextMenuDestination,
        shareDestination,
        urlDestination,
        clipboardDestination,
        urlFormat,
        floatingButtonsX,
        floatingButtonsY
      )
    }

  fun registerOnChangeListener(listener: () -> Unit) {
    val innerListener = OnSharedPreferenceChangeListener { _, _ -> listener() }
    onChangeListeners.put(listener, innerListener)
    preferences.registerOnSharedPreferenceChangeListener(innerListener)
  }

  fun unregisterOnChangeListener(listener: () -> Unit) {
    onChangeListeners.remove(listener)?.let {
      preferences.unregisterOnSharedPreferenceChangeListener(it)
    }
  }

  fun putFloatingButtonEnabled(enabled: Boolean) {
    preferences.edit().putBoolean("floating_buttons", enabled).apply()
  }

  fun putFloatingButtonPosition(x: Int, y: Int) {
    preferences.edit().putInt("floating_buttons_x", x).putInt("floating_buttons_y", y).apply()
  }

  private fun SharedPreferences.getDestination(key: String) =
    when (getString(key, "disabled")) {
      "disabled" -> Destination.DISABLED
      "clipboard" -> Destination.CLIPBOARD
      "url" -> Destination.URL
      "share" -> Destination.SHARE
      "yomichan" -> Destination.YOMICHAN
      else -> throw IllegalArgumentException()
    }

  companion object {
    fun getInstance(context: Context) =
      instanceLock.withLock {
        val thisInstance = instance
        if (thisInstance === null) {
          val defaultUrlFormat = context.getString(R.string.url_format_default)
          val newInstance =
            TextbenderPreferences(
              PreferenceManager.getDefaultSharedPreferences(context),
              defaultUrlFormat
            )
          instance = newInstance
          newInstance
        } else {
          thisInstance
        }
      }
  }
}
