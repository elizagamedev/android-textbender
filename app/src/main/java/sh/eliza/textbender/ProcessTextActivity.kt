package sh.eliza.textbender

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ProcessTextActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val preferences = TextbenderPreferences.createFromContext(this)

    val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
    if (!processText.isNullOrEmpty()) {
      Textbender.handleText(
        applicationContext,
        preferences,
        preferences.globalContextMenuDestination,
        processText
      )
    }

    val shareText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
    if (!shareText.isNullOrEmpty()) {
      Textbender.handleText(
        applicationContext,
        preferences,
        preferences.shareDestination,
        shareText
      )
    }
    finish()
  }
}
