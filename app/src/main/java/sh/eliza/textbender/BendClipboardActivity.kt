package sh.eliza.textbender

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class BendClipboardActivity : AppCompatActivity() {
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    val preferences = TextbenderPreferences.getInstance(this).snapshot
    if (hasFocus) {
      val text =
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
          .primaryClip
          ?.getItemAt(0)
          ?.coerceToText(this)
      if (preferences.clipboardDestination == TextbenderPreferences.Destination.DISABLED) {
        startActivity(
          Intent(this, SettingsActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        )
      } else {
        if (!text.isNullOrEmpty()) {
          Textbender.handleText(this, preferences, preferences.clipboardDestination, text)
        }
      }
      finish()
    }
  }
}
