package sh.eliza.textbender

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout

private const val TAG = "TextbenderService"

class TextbenderService : AccessibilityService() {
  private lateinit var windowManager: WindowManager

  private var snapshot: Snapshot? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
  }

  override fun onServiceConnected() {
    accessibilityButtonController.registerAccessibilityButtonCallback(
      object : AccessibilityButtonCallback() {
        override fun onAvailabilityChanged(
          controller: AccessibilityButtonController,
          available: Boolean
        ) {}

        override fun onClicked(controller: AccessibilityButtonController) {
          activate()
        }
      }
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    snapshot?.close()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {}

  override fun onInterrupt() {}

  private fun activate() {
    snapshot?.close()
    snapshot =
      Snapshot(this.applicationContext, windowManager, windows) {
        snapshot?.close()
        snapshot = null
      }
  }
}

private data class TextArea(
  val text: CharSequence,
  val bounds: Rect,
)

private class Snapshot(
  private val context: Context,
  private val windowManager: WindowManager,
  windows: List<AccessibilityWindowInfo>,
  private val onQuit: () -> Unit,
) : AutoCloseable {
  private val textAreas = run {
    val textAreas = mutableListOf<TextArea>()

    fun AccessibilityNodeInfo.recurse() {
      if (!isVisibleToUser()) {
        return
      }
      val text = text
      if (!text.isNullOrBlank()) {
        textAreas.add(TextArea(text, Rect().apply { getBoundsInScreen(this) }))
      }
      for (child in this) {
        child.recurse()
      }
    }

    for (window in windows) {
      window.root?.let { it.recurse() }
    }

    textAreas
  }

  private val globalLayoutListener = OnGlobalLayoutListener { onGlobalLayout() }

  private val view =
    FrameLayout(context).apply {
      layoutParams =
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
      setOnTouchListener { _, _ ->
        onQuit()
        true
      }
      viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

  init {
    windowManager.addView(
      view,
      WindowManager.LayoutParams(
          WindowManager.LayoutParams.MATCH_PARENT,
          WindowManager.LayoutParams.MATCH_PARENT,
          WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
          PixelFormat.TRANSLUCENT
        )
        .apply { gravity = Gravity.TOP or Gravity.CENTER }
    )
  }

  override fun close() {
    view.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
    windowManager.removeView(view)
  }

  private fun onGlobalLayout() {
    view.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)

    val boundsInScreen = intArrayOf(0, 0).apply { view.getLocationOnScreen(this) }

    for (textArea in textAreas) {
      view.addView(
        View(context).apply {
          x = (textArea.bounds.left - boundsInScreen[0]).toFloat()
          y = (textArea.bounds.top - boundsInScreen[1]).toFloat()
          layoutParams = ViewGroup.LayoutParams(textArea.bounds.width(), textArea.bounds.height())
          setBackgroundResource(R.drawable.textarea)
        }
      )
    }
  }
}

private operator fun AccessibilityNodeInfo.iterator() =
  object : Iterator<AccessibilityNodeInfo> {
    var index = 0

    override fun hasNext() = maybeNext() !== null

    override fun next(): AccessibilityNodeInfo {
      val (nextItem, nextIndex) = maybeNext()!!
      index = nextIndex + 1
      return nextItem
    }

    /**
     * Sometimes `getChild()` returns null. This is an ugly workaround to make this pleasant to work
     * with kotlin iterators.
     */
    private fun maybeNext(): Pair<AccessibilityNodeInfo, Int>? {
      if (index >= childCount) {
        return null
      }
      var index = index
      do {
        val child = getChild(index)
        if (child != null) {
          return Pair(child, index)
        }
        index++
      } while (index < childCount)

      return null
    }
  }
