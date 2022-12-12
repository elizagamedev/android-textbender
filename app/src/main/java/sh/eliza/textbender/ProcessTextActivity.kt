package sh.eliza.textbender

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.net.URLDecoder

private const val URL_PREFIX = "textbender://x?x="

class ProcessTextActivity : Activity() {
  private val toaster = Toaster(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val preferences = TextbenderPreferences.getInstance(applicationContext).snapshot

    when (intent.action) {
      Intent.ACTION_PROCESS_TEXT -> {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        Textbender.handleText(
          applicationContext,
          toaster,
          preferences,
          preferences.globalContextMenuDestination,
          text
        )
      }
      Intent.ACTION_SEND -> {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        Textbender.handleText(
          applicationContext,
          toaster,
          preferences,
          preferences.shareDestination,
          text
        )
      }
      Intent.ACTION_VIEW -> {
        val url = intent.data.toString()
        if (url.lowercase().startsWith(URL_PREFIX)) {
          val text = URLDecoder.decode(url.substring(URL_PREFIX.length), Charsets.UTF_8.name())
          Textbender.handleText(
            applicationContext,
            toaster,
            preferences,
            preferences.urlDestination,
            text
          )
        }
      }
    }

    finish()
  }
}
