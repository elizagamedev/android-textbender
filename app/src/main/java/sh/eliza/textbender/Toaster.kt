package sh.eliza.textbender

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** All toasters toast toast. */
class Toaster(private val context: Context) {
  private val mainHandler = Handler(Looper.getMainLooper())

  fun show(text: String, duration: Int) {
    mainHandler.post { Toast.makeText(context, text, duration).show() }
  }
}
