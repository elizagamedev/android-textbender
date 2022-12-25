package sh.eliza.textbender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.net.URLEncoder
import kotlin.text.Regex
import kotlin.text.RegexOption

private const val TAG = "Textbender"

object Textbender {
  fun handleText(
    context: Context,
    toaster: Toaster,
    preferences: TextbenderPreferences.Snapshot,
    destination: TextbenderPreferences.Destination,
    text: CharSequence?
  ) {
    val stripRegexp =
      if (preferences.stripRegexp.isEmpty()) {
        null
      } else {
        try {
          Regex(preferences.stripRegexp, setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        } catch (t: Throwable) {
          toaster.show(context.getString(R.string.invalid_regexp, t.message), Toast.LENGTH_LONG)
          null
        }
      }
    val strippedText =
      if (stripRegexp === null || text === null) {
        text
      } else {
        stripRegexp.replace(text, "")
      }
    if (strippedText.isNullOrEmpty()) {
      toaster.show(context.getString(R.string.text_empty), Toast.LENGTH_SHORT)
      return
    }
    when (destination) {
      TextbenderPreferences.Destination.DISABLED -> {}
      TextbenderPreferences.Destination.CLIPBOARD -> {
        val clipboardManager =
          context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(context.getString(R.string.app_name), text)
        clipboardManager.setPrimaryClip(clipData)
      }
      TextbenderPreferences.Destination.URL -> {
        val uriText = URLEncoder.encode(strippedText.toString(), Charsets.UTF_8.name())
        val uri = Uri.parse(preferences.urlFormat.replace("{text}", uriText))
        openUri(context, toaster, uri)
      }
      TextbenderPreferences.Destination.SHARE -> {
        val intent =
          Intent(Intent.ACTION_SEND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setType("text/plain")
            putExtra(Intent.EXTRA_TEXT, strippedText)
          }
        context.startActivity(
          Intent.createChooser(intent, context.getString(R.string.app_name)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
        )
      }
      TextbenderPreferences.Destination.PLECO -> {
        val uriText = URLEncoder.encode(strippedText.toString(), Charsets.UTF_8.name())
        val uri = Uri.parse("plecoapi://x-callback-url/s?q=$uriText")
        openUri(context, toaster, uri)
      }
      TextbenderPreferences.Destination.YOMICHAN -> openInYomichan(context, toaster, strippedText)
    }
  }
}

private fun openUri(context: Context, toaster: Toaster, uri: Uri) {
  Log.i(TAG, "Opening URI: ${uri}")
  val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
  if (intent.resolveActivity(context.packageManager) !== null) {
    context.startActivity(intent)
  } else {
    toaster.show(context.getString(R.string.could_not_open_uri, uri.toString()), Toast.LENGTH_LONG)
  }
}

private fun openInYomichan(context: Context, toaster: Toaster, text: CharSequence) {
  // Launch Kiwi browser.
  val uri = Uri.parse("googlechrome://navigate?url=")
  val intent =
    Intent(Intent.ACTION_VIEW, uri).apply {
      setPackage("com.kiwibrowser.browser")
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
  if (intent.resolveActivity(context.packageManager) !== null) {
    context.startActivity(intent)
  } else {
    toaster.show(context.getString(R.string.could_not_open_kiwi_browser), Toast.LENGTH_SHORT)
    return
  }
  val service = TextbenderService.instance
  if (service !== null) {
    service.openYomichan(text)
  } else {
    toaster.show(
      context.getString(R.string.could_not_access_accessibility_service),
      Toast.LENGTH_SHORT
    )
    return
  }
}
