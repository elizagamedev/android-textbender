package sh.eliza.textbender

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BendClipboardActivity : AppCompatActivity() {
  private val toaster = Toaster(this)

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    val preferences = TextbenderPreferences.getInstance(this).snapshot
    if (hasFocus) {
      val text =
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
          .primaryClip
          ?.getItemAt(0)
          ?.coerceToText(this)
      if (preferences.clipboardDestination == TextbenderPreferences.Destination.DISABLED) {
        toaster.show(getString(R.string.clipboard_not_configured), Toast.LENGTH_SHORT)
        startActivity(
          Intent(this, SettingsActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        )
      } else {
        Textbender.handleText(this, toaster, preferences, preferences.clipboardDestination, text)
      }
      finish()
    }
  }
}
