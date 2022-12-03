package sh.eliza.textbender

import android.content.Intent
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import kotlin.math.abs

private const val DRAG_DIAMETER_DP = 20f

class FloatingButton(private val service: TextbenderService) : AutoCloseable {
  private val button: Button =
    Button(service.applicationContext).apply {
      text = service.applicationContext.getString(R.string.app_name)
      setOnClickListener { _ -> onClick() }
      setOnLongClickListener { _ ->
        onLongClick()
        !isDragging
      }
      setOnTouchListener { _, event ->
        onTouch(event)
        false
      }
    }

  private val layoutParams =
    WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    )

  val dragDiameterPx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_PX,
      DRAG_DIAMETER_DP,
      service.applicationContext.resources.displayMetrics
    )

  init {
    TextbenderPreferences.createFromContext(service.applicationContext).let {
      layoutParams.x = it.floatingButtonX
      layoutParams.y = it.floatingButtonY
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
        TextbenderPreferences.putFloatingButton(
          service.applicationContext,
          layoutParams.x,
          layoutParams.y
        )
      }
      else -> {}
    }
  }
}
