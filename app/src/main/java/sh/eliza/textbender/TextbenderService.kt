package sh.eliza.textbender

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "TextbenderService"

private val atomicInstance = AtomicReference<TextbenderService>()

class TextbenderService : AccessibilityService() {
  private val handlerThread = HandlerThread(TAG).apply { start() }
  val handler = Handler(handlerThread.looper)

  private val onPreferenceChangeListener: () -> Unit = {
    handler.post {
      setFloatingButton(
        TextbenderPreferences.createFromContext(applicationContext).floatingButtonEnabled
      )
    }
  }

  private lateinit var windowManager: WindowManager

  private var snapshot: Snapshot? = null
  private val openYomichanStateMachine = AtomicReference<OpenYomichanStateMachine>()

  private var floatingButton: Button? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
  }

  override fun onServiceConnected() {
    atomicInstance.set(this)

    accessibilityButtonController.registerAccessibilityButtonCallback(
      object : AccessibilityButtonCallback() {
        override fun onAvailabilityChanged(
          controller: AccessibilityButtonController,
          available: Boolean
        ) {}

        override fun onClicked(controller: AccessibilityButtonController) {
          if (TextbenderPreferences.createFromContext(applicationContext)
              .accessibilityShortcutEnabled
          ) {
            toggleOverlay()
          }
        }
      }
    )

    setFloatingButton(
      TextbenderPreferences.createFromContext(applicationContext).floatingButtonEnabled
    )

    TextbenderPreferences.registerOnChangeListener(applicationContext, onPreferenceChangeListener)
  }

  override fun onUnbind(intent: Intent): Boolean {
    TextbenderPreferences.unregisterOnChangeListener(applicationContext, onPreferenceChangeListener)
    handlerThread.run {
      quitSafely()
      join()
    }
    atomicInstance.compareAndSet(this, null)
    openYomichanStateMachine.getAndSet(null)?.close()
    return false
  }

  override fun onDestroy() {
    super.onDestroy()
    snapshot?.close()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {}

  override fun onInterrupt() {}

  fun openYomichan(text: CharSequence) {
    handler.post { handleOpenYomichan(text) }
  }

  private fun handleOpenYomichan(text: CharSequence) {
    openYomichanStateMachine
      .getAndSet(
        OpenYomichanStateMachine(this, text) {
          it.close()
          openYomichanStateMachine.compareAndSet(it, null)
        }
      )
      ?.close()
  }

  private fun toggleOverlay() {
    val snapshot = snapshot
    snapshot?.close()
    if (snapshot !== null) {
      this.snapshot = null
    } else {
      this.snapshot =
        Snapshot(this.applicationContext, windowManager, windows) {
          this.snapshot?.close()
          this.snapshot = null
        }
    }
  }

  private fun setFloatingButton(enabled: Boolean) {
    if (enabled) {
      if (floatingButton !== null) {
        return
      }
      val button =
        Button(applicationContext).apply {
          text = "Hello"
          setOnTouchListener { _, event -> true }
        }
      windowManager.addView(
        button,
        WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
          PixelFormat.TRANSLUCENT
        )
      )
      floatingButton = button
    } else {
      floatingButton?.let {
        windowManager.removeView(it)
        floatingButton = null
      }
    }
  }

  companion object {
    val instance: TextbenderService?
      get() = atomicInstance.get()
  }
}
