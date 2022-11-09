package sh.eliza.textbender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URLEncoder

private const val TAG = "Textbender"

object Textbender {
  fun handleText(
    context: Context,
    preferences: TextbenderPreferences,
    destination: TextbenderPreferences.Destination,
    text: CharSequence
  ) {
    when (destination) {
      TextbenderPreferences.Destination.CLIPBOARD -> {
        val clipboardManager =
          context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(context.getString(R.string.app_name), text)
        clipboardManager.setPrimaryClip(clipData)
      }
      TextbenderPreferences.Destination.URL -> {
        val uri =
          Uri.parse(
            preferences.urlFormat.replace(
              "{text}",
              URLEncoder.encode(text.toString(), Charsets.UTF_8.name())
            )
          )
        Log.i(TAG, "Opening URI: ${uri}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        if (intent.resolveActivity(context.packageManager) !== null) {
          context.startActivity(intent)
        }
      }
      TextbenderPreferences.Destination.SHARE -> {
        val intent =
          Intent(Intent.ACTION_SEND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setType("text/plain")
            putExtra(Intent.EXTRA_TEXT, text)
          }
        context.startActivity(
          Intent.createChooser(intent, context.getString(R.string.app_name)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
        )
      }
      TextbenderPreferences.Destination.YOMICHAN -> {
        // Launch Kiwi browser.
        val uri = Uri.parse("googlechrome://navigate?url=")
        val intent =
          Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.kiwibrowser.browser")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
        if (intent.resolveActivity(context.packageManager) !== null) {
          context.startActivity(intent)
        }
        // Copy to clipboard.
        handleText(context, preferences, TextbenderPreferences.Destination.CLIPBOARD, text)
      }
    }
  }
}
