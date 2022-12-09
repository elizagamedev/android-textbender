package sh.eliza.textbender

import android.content.Context
import android.graphics.PixelFormat
import android.text.TextUtils.TruncateAt
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.max

private const val TAG = "Snapshot"

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
private const val DEFAULT_TEXT_SIZE_DP = 18f

/** Minimum length of a string to be considered "wrappable". */
private const val MIN_WRAPPABLE_LENGTH = 32

private data class TextArea(
  val text: CharSequence,
  val textSize: Float,
  val bounds: ImmutableRect,
)

class Snapshot(
  private val context: Context,
  private val windowManager: WindowManager,
  windows: List<AccessibilityWindowInfo>,
  private val onQuit: () -> Unit,
) : AutoCloseable {
  private val preferences = TextbenderPreferences.getInstance(context)

  private val growPaddingPx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      GROW_PADDING_DP,
      context.resources.displayMetrics
    )

  private val paddingPx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      PADDING_DP,
      context.resources.displayMetrics
    )

  private val defaultTextSizePx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      DEFAULT_TEXT_SIZE_DP,
      context.resources.displayMetrics
    )

  private val impossiblySmallTextSizePx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      IMPOSSIBLY_SMALL_TEXT_SIZE_DP,
      context.resources.displayMetrics
    )

  private val density = context.resources.displayMetrics.density

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
        performClick()
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
          Button(
              context,
              /*attrs=*/ null,
              /*defStyleAttr=*/ 0,
              /*defStyleRes=*/ R.style.Widget_Button_TextOverlay
            )
            .apply {
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
                val preferencesSnapshot = preferences.snapshot
                Textbender.handleText(
                  context,
                  preferencesSnapshot,
                  preferencesSnapshot.tapDestination,
                  textArea.text
                )
                onQuit()
              }

              setOnLongClickListener {
                val preferencesSnapshot = preferences.snapshot
                if (preferencesSnapshot.longPressDestination !=
                    TextbenderPreferences.Destination.DISABLED
                ) {
                  Textbender.handleText(
                    context,
                    preferencesSnapshot,
                    preferencesSnapshot.longPressDestination,
                    textArea.text
                  )
                  onQuit()
                  true
                } else {
                  false
                }
              }
            }
        )
      }
    }
  }
}
