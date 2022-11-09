package sh.eliza.textbender

import android.os.Handler
import android.os.HandlerThread

private const val TAG = "ServiceServer"

class ServiceServer(val service: TextbenderService) : AutoCloseable {
  private val handlerThread = HandlerThread(TAG).apply { start() }
  private val handler = Handler(handlerThread.looper)

  override fun close() {
    handlerThread.run {
      quit()
      join()
    }
  }

  fun openYomichan(text: CharSequence) {
    handler.post { service.openYomichan(text) }
  }
}
