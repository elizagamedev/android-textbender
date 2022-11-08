package sh.eliza.textbender

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
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
  val bounds: ImmutableRect,
)

private class Snapshot(
  private val context: Context,
  private val windowManager: WindowManager,
  windows: List<AccessibilityWindowInfo>,
  private val onQuit: () -> Unit,
) : AutoCloseable {
  private val textAreas = run {
    val occlusionBuffer = OcclusionBuffer()
    val textAreas = mutableListOf<TextArea>()

    fun recurse(node: AccessibilityNodeInfo) {
      if (!node.isVisibleToUser()) {
        return
      }
      val boundsInScreen = node.boundsInScreen
      val text = node.text
      val children = node.children
      if (!children.isEmpty()) {
        if (!occlusionBuffer.isPartiallyVisible(boundsInScreen)) {
          return
        }
        for (child in children) {
          recurse(child)
        }
      }
      if (occlusionBuffer.add(boundsInScreen) && !text.isNullOrBlank()) {
        textAreas.add(TextArea(text, boundsInScreen))
      }
    }

    for (window in windows) {
      window.root?.let { recurse(it) }
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

    val boundsInScreen = view.boundsInScreen

    for (textArea in textAreas) {
      view.addView(
        View(context).apply {
          val bounds = textArea.bounds.intersect(boundsInScreen)
          if (bounds != null) {
            x = (bounds.left - boundsInScreen.left).toFloat()
            y = (bounds.top - boundsInScreen.top).toFloat()
            layoutParams = ViewGroup.LayoutParams(bounds.width, bounds.height)
            setBackgroundResource(R.drawable.textarea)
          }
        }
      )
    }
  }
}
