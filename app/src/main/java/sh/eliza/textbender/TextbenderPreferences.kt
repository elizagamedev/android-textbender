package sh.eliza.textbender

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

data class TextbenderPreferences(
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
  }

  companion object {
    fun createFromContext(context: Context): TextbenderPreferences {
      val preferences = PreferenceManager.getDefaultSharedPreferences(context)
      val tapDestination = preferences.getDestination("tap_destination")
      val longPressDestination = preferences.getDestination("long_press_destination")
      val globalContextMenuDestination =
        preferences.getDestination("global_context_menu_destination")
      val shareDestination = preferences.getDestination("share_destination")
      val urlDestination = preferences.getDestination("url_destination")
      val urlFormat =
        preferences.getString("url_format", null) ?: context.getString(R.string.url_format_default)
      return TextbenderPreferences(
        tapDestination,
        longPressDestination,
        globalContextMenuDestination,
        shareDestination,
        urlDestination,
        urlFormat
      )
    }

    private fun SharedPreferences.getDestination(key: String) =
      when (getString(key, "clipboard")) {
        "clipboard" -> Destination.CLIPBOARD
        "url" -> Destination.URL
        "share" -> Destination.SHARE
        else -> throw IllegalArgumentException()
      }
  }
}
