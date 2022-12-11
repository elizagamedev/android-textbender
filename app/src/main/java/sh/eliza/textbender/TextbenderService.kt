package sh.eliza.textbender

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "TextbenderService"

class TextbenderService : AccessibilityService() {
  private val handlerThread = HandlerThread(TAG).apply { start() }
  val handler = Handler(handlerThread.looper)
  val toaster = Toaster(this)

  lateinit var preferences: TextbenderPreferences
  lateinit var windowManager: WindowManager

  private var snapshot: Snapshot? = null
  private var previousPreferencesSnapshot: TextbenderPreferences.Snapshot? = null
  private val openYomichanStateMachine = AtomicReference<OpenYomichanStateMachine>()

  private var floatingButtons: FloatingButtons? = null

  override fun onCreate() {
    super.onCreate()
    preferences = TextbenderPreferences.getInstance(applicationContext)
    previousPreferencesSnapshot = preferences.defaults
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
  }

  override fun onServiceConnected() {
    atomicInstance.set(this)
    for ((listener, listenerHandler) in onInstanceChangedListeners) {
      listenerHandler.post { listener(this) }
    }

    accessibilityButtonController.registerAccessibilityButtonCallback(
      object : AccessibilityButtonCallback() {
        override fun onAvailabilityChanged(
          controller: AccessibilityButtonController,
          available: Boolean
        ) {}

        override fun onClicked(controller: AccessibilityButtonController) {
          if (snapshot === null) {
            openOverlay()
          }
        }
      }
    )

    handler.post { resetFloatingButton(preferences.snapshot) }

    preferences.addOnChangeListener(this::onPreferenceChange, handler)
  }

  override fun onUnbind(intent: Intent): Boolean {
    preferences.removeOnChangeListener(this::onPreferenceChange)
    handlerThread.run {
      quitSafely()
      join()
    }
    if (atomicInstance.compareAndSet(this, null)) {
      for ((listener, listenerHandler) in onInstanceChangedListeners) {
        listenerHandler.post { listener(null) }
      }
    }
    openYomichanStateMachine.getAndSet(null)?.close()
    return false
  }

  override fun onDestroy() {
    super.onDestroy()
    snapshot?.close()
    floatingButtons?.close()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {}

  override fun onInterrupt() {}

  fun openYomichan(text: CharSequence) {
    handler.post { handleOpenYomichan(text) }
  }

  fun openOverlay(delayMs: Long = 0L) {
    if (delayMs <= 0) {
      handler.post(this::handleOpenOverlay)
    } else {
      handler.postDelayed(this::handleOpenOverlay, delayMs)
    }
  }

  private fun onPreferenceChange(preferencesSnapshot: TextbenderPreferences.Snapshot) {
    resetFloatingButton(preferencesSnapshot)
    previousPreferencesSnapshot = preferencesSnapshot
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

  private fun handleOpenOverlay() {
    floatingButtons?.close()
    floatingButtons = null
    snapshot?.close()
    snapshot =
      Snapshot(applicationContext, toaster, windowManager, windows) {
        this.snapshot?.close()
        this.snapshot = null
        resetFloatingButton(preferences.snapshot)
      }
  }

  private fun resetFloatingButton(preferencesSnapshot: TextbenderPreferences.Snapshot) {
    val previousPreferencesSnapshot = previousPreferencesSnapshot ?: preferences.defaults
    val enabled =
      preferencesSnapshot.floatingButtonsEnabled && !preferencesSnapshot.floatingButtonsEmpty
    val changed =
      preferencesSnapshot.floatingButtonOverlayEnabled !=
        previousPreferencesSnapshot.floatingButtonOverlayEnabled ||
        preferencesSnapshot.floatingButtonClipboardEnabled !=
          previousPreferencesSnapshot.floatingButtonClipboardEnabled ||
        preferencesSnapshot.floatingButtonsOpacity !=
          previousPreferencesSnapshot.floatingButtonsOpacity ||
        (enabled && floatingButtons === null) ||
        (!enabled && floatingButtons !== null)
    if (changed) {
      floatingButtons?.close()
      floatingButtons = null
      if (enabled) {
        floatingButtons = FloatingButtons(this)
      }
    }
  }

  companion object {
    val instance: TextbenderService?
      get() = atomicInstance.get()

    private val atomicInstance = AtomicReference<TextbenderService>()
    private val onInstanceChangedListeners =
      ConcurrentHashMap<(TextbenderService?) -> Unit, Handler>()

    fun addOnInstanceChangedListener(listener: (TextbenderService?) -> Unit, handler: Handler) {
      onInstanceChangedListeners.put(listener, handler)
    }

    fun removeOnInstanceChangedListener(listener: (TextbenderService?) -> Unit) {
      onInstanceChangedListeners.remove(listener)
    }
  }
}
