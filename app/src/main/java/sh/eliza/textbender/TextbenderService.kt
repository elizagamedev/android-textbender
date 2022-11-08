package sh.eliza.textbender

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.text.TextUtils.TruncateAt
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.max

private const val TAG = "TextbenderService"

/**
 * Shrink the text size by this amount to add a slight bit of artificial vertical padding to the
 * buttons, and add equal horizontal padding.
 */
private const val PADDING_DP = 2f

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
          toggle()
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

  private fun toggle() {
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
}

private data class TextArea(
  val text: CharSequence,
  val textSize: Float?,
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
      val textSize = node.textSizeInPx
      if (occlusionBuffer.add(boundsInScreen) && !text.isNullOrBlank() && textSize !== 0f) {
        val preciseBounds = node.textBounds ?: boundsInScreen
        textAreas.add(TextArea(text, textSize, preciseBounds))
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

    val paddingPx =
      TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        PADDING_DP,
        context.resources.displayMetrics
      )
    val boundsInScreen = view.boundsInScreen

    for (textArea in textAreas) {
      textArea.bounds.intersect(boundsInScreen)?.let { bounds ->
        view.addView(
          Button(context, null, 0, R.style.button_textarea).apply {
            text = textArea.text
            x = (bounds.left - boundsInScreen.left).toFloat()
            y = (bounds.top - boundsInScreen.top).toFloat()
            layoutParams = ViewGroup.LayoutParams(bounds.width, bounds.height)

            // If a single line, truncate + add ellipsis.
            if (!textArea.text.contains('\n')) {
              ellipsize = TruncateAt.END
              maxLines = 1
            }

            // Tweaking the text size works well enough for simulating vertical padding, but not
            // horizontal.
            setPadding(paddingPx.toInt(), 0, paddingPx.toInt(), 0)

            textArea.textSize?.let {
              setTextSize(TypedValue.COMPLEX_UNIT_PX, max(it - paddingPx, 1f))
            }
          }
        )
      }
    }
  }
}
