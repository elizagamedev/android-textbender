package sh.eliza.textbender

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import androidx.preference.PreferenceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "TextbenderPreferences"

private val instanceLock = ReentrantLock()
private var instance: TextbenderPreferences? = null

class TextbenderPreferences
private constructor(
  private val preferences: SharedPreferences,
  val defaults: Snapshot,
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
  ) {
    val floatingButtonsEmpty = !floatingButtonOverlayEnabled && !floatingButtonClipboardEnabled
  }

  private val latestAtomicSnapshot = AtomicReference<Snapshot>(createSnapshot())
  private val onChangeListeners = ConcurrentHashMap<(Snapshot) -> Unit, Handler>()
  private val internalListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
      val snapshot = createSnapshot()
      latestAtomicSnapshot.set(snapshot)
      for ((listener, listenerHandler) in onChangeListeners) {
        listenerHandler.post { listener(snapshot) }
      }
    }

  val snapshot
    get() = latestAtomicSnapshot.get()

  init {
    preferences.registerOnSharedPreferenceChangeListener(internalListener)
  }

  fun addOnChangeListener(listener: (Snapshot) -> Unit, handler: Handler) {
    onChangeListeners.put(listener, handler)
  }

  fun removeOnChangeListener(listener: (Snapshot) -> Unit) {
    onChangeListeners.remove(listener)
  }

  fun putFloatingButtonEnabled(enabled: Boolean) {
    preferences.edit().apply {
      putBoolean("floating_buttons", enabled)
      apply()
    }
  }

  fun putFloatingButtonPosition(x: Int, y: Int) {
    preferences.edit().apply {
      putInt("floating_buttons_x", x)
      putInt("floating_buttons_y", y)
      apply()
    }
  }

  private fun createSnapshot(): Snapshot {
    // UI Options
    val floatingButtonsEnabled =
      preferences.getBoolean("floating_buttons", defaults.floatingButtonsEnabled)
    val floatingButtonOverlayEnabled =
      preferences.getBoolean("floating_button_overlay", defaults.floatingButtonOverlayEnabled)
    val floatingButtonClipboardEnabled =
      preferences.getBoolean("floating_button_clipboard", defaults.floatingButtonClipboardEnabled)

    // Mappings
    val tapDestination = preferences.getDestination("tap_destination", defaults.tapDestination)
    val longPressDestination =
      preferences.getDestination("long_press_destination", defaults.longPressDestination)
    val globalContextMenuDestination =
      preferences.getDestination(
        "global_context_menu_destination",
        defaults.globalContextMenuDestination
      )
    val shareDestination =
      preferences.getDestination("share_destination", defaults.shareDestination)
    if (shareDestination === Destination.SHARE) {
      throw IllegalArgumentException()
    }
    val urlDestination = preferences.getDestination("url_destination", defaults.urlDestination)
    val clipboardDestination =
      preferences.getDestination("clipboard_destination", defaults.clipboardDestination)
    if (clipboardDestination === Destination.CLIPBOARD) {
      throw IllegalArgumentException()
    }

    // Destionation Options
    val urlFormat = preferences.getString("url_format", null) ?: defaults.urlFormat

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

  private fun SharedPreferences.getDestination(key: String, default: Destination) =
    when (getString(key, null)) {
      "disabled" -> Destination.DISABLED
      "clipboard" -> Destination.CLIPBOARD
      "url" -> Destination.URL
      "share" -> Destination.SHARE
      "yomichan" -> Destination.YOMICHAN
      null -> default
      else -> throw IllegalArgumentException()
    }

  companion object {
    fun getInstance(context: Context) =
      instanceLock.withLock {
        val thisInstance = instance
        if (thisInstance === null) {

          val defaults =
            Snapshot(
              floatingButtonsEnabled = false,
              floatingButtonOverlayEnabled = false,
              floatingButtonClipboardEnabled = false,
              tapDestination = Destination.DISABLED,
              longPressDestination = Destination.DISABLED,
              globalContextMenuDestination = Destination.DISABLED,
              shareDestination = Destination.DISABLED,
              urlDestination = Destination.DISABLED,
              clipboardDestination = Destination.DISABLED,
              urlFormat = context.getString(R.string.url_format_default),
              // Hidden preferences.
              floatingButtonsX = 0,
              floatingButtonsY = 0,
            )

          val newInstance =
            TextbenderPreferences(PreferenceManager.getDefaultSharedPreferences(context), defaults)
          instance = newInstance
          newInstance
        } else {
          thisInstance
        }
      }
  }
}
