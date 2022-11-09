package sh.eliza.textbender

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
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
import android.widget.Toast
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

private const val TAG = "TextbenderService"

private const val YOMICHAN_URL_PREFIX =
  "chrome-extension://ogmnaimimemjmbakcfefmnahgdfhfami/search.html?full="

/**
 * Grow the "precise" view area by this amount, not exceeding the bounds of the parent view. This
 * makes small text areas which are part of huge views easier to press.
 */
private const val GROW_PADDING_DP = 8f

/**
 * Shrink the text size by this amount to add a slight bit of artificial vertical padding to the
 * buttons, and add equal horizontal padding.
 */
private const val PADDING_DP = 2f

/** Size considered "impossibly small", i.e. bad data. */
private const val IMPOSSIBLY_SMALL_TEXT_SIZE_DP = 8f

/** Default text size if an impossibly low value is returned. */
private const val DEFAULT_TEXT_SIZE_DP = 32f

/** Minimum length of a string to be considered "wrappable". */
private const val MIN_WRAPPABLE_LENGTH = 32

private val instance = AtomicReference<TextbenderService>()

class TextbenderService : AccessibilityService() {
  private val mainHandler = Handler(Looper.getMainLooper())

  private lateinit var server: ServiceServer
  private lateinit var windowManager: WindowManager

  private var snapshot: Snapshot? = null
  private val openYomichanStateMachine = AtomicReference<OpenYomichanStateMachine>()

  override fun onCreate() {
    super.onCreate()
    server = ServiceServer(this)
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
  }

  override fun onServiceConnected() {
    instance.set(this)

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

  override fun onUnbind(intent: Intent): Boolean {
    instance.compareAndSet(this, null)
    openYomichanStateMachine.getAndSet(null)?.close()
    return false
  }

  override fun onDestroy() {
    super.onDestroy()
    server.close()
    snapshot?.close()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {}

  override fun onInterrupt() {}

  fun openYomichan(text: CharSequence) {
    openYomichanStateMachine.getAndSet(OpenYomichanStateMachine(this, text))?.close()
  }

  fun makeToast(message: String) {
    mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
  }

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

  companion object {
    val serverInstance: ServiceServer?
      get() = instance.get()?.server
  }
}

private data class TextArea(
  val text: CharSequence,
  val textSize: Float,
  val bounds: ImmutableRect,
)

private class Snapshot(
  private val context: Context,
  private val windowManager: WindowManager,
  windows: List<AccessibilityWindowInfo>,
  private val onQuit: () -> Unit,
) : AutoCloseable {

  val growPaddingPx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      GROW_PADDING_DP,
      context.resources.displayMetrics
    )

  val paddingPx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_PX,
      PADDING_DP,
      context.resources.displayMetrics
    )

  val defaultTextSizePx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_PX,
      DEFAULT_TEXT_SIZE_DP,
      context.resources.displayMetrics
    )

  val impossiblySmallTextSizePx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_PX,
      IMPOSSIBLY_SMALL_TEXT_SIZE_DP,
      context.resources.displayMetrics
    )

  val density = context.resources.displayMetrics.density

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
      val textSize = run {
        val size = node.textSizeInPx
        if (size === null || size < impossiblySmallTextSizePx) {
          defaultTextSizePx
        } else {
          size
        }
      }
      if (occlusionBuffer.add(boundsInScreen) && !text.isNullOrBlank()) {
        val textBounds = node.textBounds
        val preciseBounds =
          if (textBounds === null) {
            boundsInScreen
          } else {
            textBounds.inset(-growPaddingPx.toInt()).intersect(boundsInScreen) ?: boundsInScreen
          }
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

    val boundsInScreen = view.boundsInScreen

    for (textArea in textAreas) {
      textArea.bounds.intersect(boundsInScreen)?.let { bounds ->
        view.addView(
          Button(context, null, 0, R.style.button_textarea).apply {
            text = textArea.text
            x = (bounds.left - boundsInScreen.left).toFloat()
            y = (bounds.top - boundsInScreen.top).toFloat()
            layoutParams = ViewGroup.LayoutParams(bounds.width, bounds.height)

            // If a single line, truncate + add ellipsis + center.
            if (textArea.text.length < MIN_WRAPPABLE_LENGTH && !textArea.text.contains('\n')) {
              ellipsize = TruncateAt.END
              maxLines = 1
              gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            }

            // Tweaking the text size works well enough for simulating vertical padding, but not
            // horizontal.
            setPadding(paddingPx.toInt(), 0, paddingPx.toInt(), 0)

            setTextSize(TypedValue.COMPLEX_UNIT_PX, max(textArea.textSize - paddingPx, 1f))

            setOnClickListener {
              val preferences = TextbenderPreferences.createFromContext(context)
              Textbender.handleText(context, preferences, preferences.tapDestination, textArea.text)
              onQuit()
            }

            setOnLongClickListener {
              val preferences = TextbenderPreferences.createFromContext(context)
              Textbender.handleText(
                context,
                preferences,
                preferences.longPressDestination,
                textArea.text
              )
              onQuit()
              true
            }
          }
        )
      }
    }
  }
}
