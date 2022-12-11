package sh.eliza.textbender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.net.URLEncoder

private const val TAG = "Textbender"

object Textbender {
  fun handleText(
    context: Context,
    preferences: TextbenderPreferences.Snapshot,
    destination: TextbenderPreferences.Destination,
    text: CharSequence
  ) {
    when (destination) {
      TextbenderPreferences.Destination.DISABLED -> {}
      TextbenderPreferences.Destination.CLIPBOARD -> {
        val clipboardManager =
          context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(context.getString(R.string.app_name), text)
        clipboardManager.setPrimaryClip(clipData)
      }
      TextbenderPreferences.Destination.URL -> {
        val uriText = URLEncoder.encode(text.toString(), Charsets.UTF_8.name())
        val uri = Uri.parse(preferences.urlFormat.replace("{text}", uriText))
        openUri(context, uri)
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
      TextbenderPreferences.Destination.PLECO -> {
        val uriText = URLEncoder.encode(text.toString(), Charsets.UTF_8.name())
        val uri = Uri.parse("plecoapi://x-callback-url/s?q=$uriText")
        openUri(context, uri)
      }
      TextbenderPreferences.Destination.YOMICHAN -> openInYomichan(context, text)
    }
  }
}

private fun openUri(context: Context, uri: Uri) {
  Log.i(TAG, "Opening URI: ${uri}")
  val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
  if (intent.resolveActivity(context.packageManager) !== null) {
    context.startActivity(intent)
  } else {
    context.showToast(context.getString(R.string.could_not_open_uri, uri.toString()))
  }
}

private fun openInYomichan(context: Context, text: CharSequence) {
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
    Toast.makeText(
        context,
        context.getString(R.string.could_not_open_kiwi_browser),
        Toast.LENGTH_LONG
      )
      .show()
    return
  }
  val service = TextbenderService.instance
  if (service !== null) {
    service.openYomichan(text)
  } else {
    context.showToast(context.getString(R.string.could_not_access_accessibility_service))
    return
  }
}

private fun Context.showToast(text: String) {
  Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
