package sh.eliza.textbender

import androidx.appcompat.app.AppCompatActivity

class BendClipboardActivity : AppCompatActivity() {
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    if (hasFocus) {
      Textbender.bendClipboard(this)
      finish()
    }
  }
}
