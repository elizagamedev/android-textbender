package sh.eliza.textbender

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.net.URLEncoder

private const val TAG = "OpenYomichanStateMachine"

private const val MAX_RETRIES = 10
private const val RETRY_INTERVAL_MS = 100L

private const val YOMICHAN_URL_PREFIX =
  "chrome-extension://ogmnaimimemjmbakcfefmnahgdfhfami/search.html?query="

/** Replace this with a coroutine or something eventually. */
class OpenYomichanStateMachine(
  private val service: TextbenderService,
  private val text: CharSequence,
  private val onQuit: (OpenYomichanStateMachine) -> Unit,
) : AutoCloseable {
  interface State {
    fun advance(): State?
  }

  private inner class LocateKiwiBrowserWindow : State {
    override fun advance(): State? {
      Log.i(TAG, "LocateKiwiBrowserWindow")
      // On some devices (Boox Nova Air in my case), the window's title appears to be "stale" unless
      // we query the root first, so do a mostly unnecessary check for the root window in this loop.
      val root =
        service.windows.firstOrNull { it?.root !== null && it.title == "Kiwi Browser" }?.root
      if (root === null) {
        return this
      }
      return LocateAddressBar(root)
    }
  }

  private inner class LocateAddressBar(private val root: AccessibilityNodeInfo) : State {
    override fun advance(): State? {
      Log.i(TAG, "LocateAddressBar")
      val addressBar =
        root.findAccessibilityNodeInfosByViewId("com.kiwibrowser.browser:id/url_bar")?.firstOrNull()
      if (addressBar === null) {
        return this
      }

      addressBar.performAction(AccessibilityNodeInfo.ACTION_FOCUS, Bundle())
      return Delay(1, SetAddressBarText(root, addressBar))
    }
  }

  private inner class SetAddressBarText(
    private val root: AccessibilityNodeInfo,
    private val addressBar: AccessibilityNodeInfo
  ) : State {
    override fun advance(): State? {
      val url = YOMICHAN_URL_PREFIX + URLEncoder.encode(text.toString(), Charsets.UTF_8.name())
      addressBar.performAction(
        AccessibilityNodeInfo.ACTION_SET_TEXT,
        Bundle().apply {
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, url)
        }
      )

      return LocateResult(root, url)
    }
  }

  private inner class LocateResult(
    private val root: AccessibilityNodeInfo,
    private val url: String
  ) : State {
    override fun advance(): State? {
      Log.i(TAG, "LocateGoButton")
      val result =
        root
          .findAccessibilityNodeInfosByViewId(
            "com.kiwibrowser.browser:id/omnibox_results_container"
          )
          ?.firstOrNull()
          ?.getChild(0)
          ?.getChild(0)
          ?.children
          ?.firstOrNull { it.find { it.text?.startsWith(YOMICHAN_URL_PREFIX) ?: false } !== null }
      if (result === null) {
        return this
      }
      result.performAction(AccessibilityNodeInfo.ACTION_CLICK, Bundle())
      return Delay(1, null)
    }
  }

  private inner class Delay(private val times: Int, private val nextState: State?) : State {
    private var counter = 0

    override fun advance(): State? {
      if (counter++ < times) {
        return this
      }
      return nextState
    }
  }

  val isAlive: Boolean
    get() = state !== null

  private var state: State? = LocateKiwiBrowserWindow()
  private var tries = 0

  init {
    service.softKeyboardController.showMode = AccessibilityService.SHOW_MODE_HIDDEN
    advance()
  }

  private fun advance() {
    while (true) {
      val newState =
        try {
          state?.advance()
        } catch (t: Throwable) {
          Log.e(TAG, "Exception on handler thread", t)
          state
        }
      if (state === newState) {
        break
      }
      state = newState
    }
    tries++

    val state = state
    if (state !== null) {
      if (tries < MAX_RETRIES) {
        service.handler.postDelayed(this::advance, RETRY_INTERVAL_MS)
        return
      }
      Log.i(TAG, "Giving up")
      service.toaster.show(
        service.getString(R.string.could_not_open_kiwi_browser_url, state::class.simpleName),
        Toast.LENGTH_LONG
      )
      this.state = null
    }
    onQuit(this)
  }

  override fun close() {
    state = null
    service.softKeyboardController.showMode = AccessibilityService.SHOW_MODE_AUTO
  }
}
