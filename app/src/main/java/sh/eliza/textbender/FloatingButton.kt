package sh.eliza.textbender

import android.content.Intent
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageButton
import kotlin.math.abs

private const val DRAG_DIAMETER_DP = 20f
private const val BUTTON_SIZE_DP = 128f

class FloatingButton(private val service: TextbenderService) : AutoCloseable {
  private val preferences = TextbenderPreferences.getInstance(service.applicationContext)

  private val button: ImageButton =
    ImageButton(service.applicationContext).apply {
      setImageResource(R.drawable.ic_launcher_foreground)
      adjustViewBounds = true
      setOnClickListener { _ -> onClick() }
      setOnLongClickListener { _ ->
        onLongClick()
        !isDragging
      }
      // Suppress this lint because this onTouch does not generate "clicks".
      setOnTouchListener @Suppress("ClickableViewAccessibility")
      { _, event ->
        onTouch(event)
        false
      }
    }

  private val dragDiameterPx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_PX,
      DRAG_DIAMETER_DP,
      service.applicationContext.resources.displayMetrics
    )

  private val buttonSizePx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_PX,
      BUTTON_SIZE_DP,
      service.applicationContext.resources.displayMetrics
    )

  private val layoutParams =
    WindowManager.LayoutParams(
      buttonSizePx.toInt(),
      buttonSizePx.toInt(),
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    )

  init {
    preferences.snapshot.let {
      layoutParams.x = it.floatingButtonsX
      layoutParams.y = it.floatingButtonsY
    }
  }

  private var ox = 0
  private var oy = 0
  private var px = 0f
  private var py = 0f
  private var isDragging = false

  init {
    service.windowManager.addView(button, layoutParams)
  }

  override fun close() {
    service.windowManager.removeView(button)
  }

  private fun onClick() {
    if (!isDragging) {
      service.openOverlay()
    }
  }

  private fun onLongClick() {
    if (!isDragging) {
      service.startActivity(
        Intent(service, SettingsActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
      )
    }
  }

  private fun onTouch(event: MotionEvent) {
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        ox = layoutParams.x
        oy = layoutParams.y
        px = event.rawX
        py = event.rawY
        isDragging = false
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = (event.rawX - px).toInt()
        val dy = (event.rawY - py).toInt()
        if (abs(dx) >= dragDiameterPx || abs(dy) >= dragDiameterPx) {
          isDragging = true
        }
        if (isDragging) {
          layoutParams.x = ox + dx
          layoutParams.y = oy + dy
          service.windowManager.updateViewLayout(button, layoutParams)
        }
      }
      MotionEvent.ACTION_UP -> {
        preferences.putFloatingButtonPosition(layoutParams.x, layoutParams.y)
      }
      else -> {}
    }
  }
}
