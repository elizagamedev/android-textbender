package sh.eliza.textbender

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.net.URLDecoder

private const val URL_PREFIX = "textbender://x?x="

class ProcessTextActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val preferences = TextbenderPreferences.createFromContext(this)

    when (intent.action) {
      Intent.ACTION_PROCESS_TEXT -> {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        if (!text.isNullOrEmpty()) {
          Textbender.handleText(
            applicationContext,
            preferences,
            preferences.globalContextMenuDestination,
            text
          )
        }
      }
      Intent.ACTION_SEND -> {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrEmpty()) {
          Textbender.handleText(applicationContext, preferences, preferences.shareDestination, text)
        }
      }
      Intent.ACTION_VIEW -> {
        val url = intent.data.toString()
        if (url.lowercase().startsWith(URL_PREFIX)) {
          val text = URLDecoder.decode(url.substring(URL_PREFIX.length), Charsets.UTF_8.name())
          if (!text.isNullOrEmpty()) {
            Textbender.handleText(applicationContext, preferences, preferences.urlDestination, text)
          }
        }
      }
    }

    finish()
  }
}
