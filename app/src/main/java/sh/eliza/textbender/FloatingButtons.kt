package sh.eliza.textbender

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import kotlin.math.abs

private const val DRAG_DIAMETER_DP = 16f

class FloatingButtons(private val service: TextbenderService) : AutoCloseable {
  private val preferences = TextbenderPreferences.getInstance(service.applicationContext)

  private val root: View = run {
    val layoutInflater = service.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    layoutInflater.inflate(R.layout.floating_buttons, /*root=*/ null)
  }

  private val overlayButton = root.findViewById<ImageButton>(R.id.overlay)

  private val clipboardButton = root.findViewById<ImageButton>(R.id.clipboard)

  init {
    val preferencesSnapshot = preferences.snapshot

    overlayButton.run {
      setOnClickListener { _ -> onClickOverlayButton() }
      if (!preferencesSnapshot.floatingButtonOverlayEnabled) {
        visibility = View.GONE
      }
    }

    clipboardButton.run {
      setOnClickListener { _ -> onClickClipboardButton() }
      if (!preferencesSnapshot.floatingButtonClipboardEnabled) {
        visibility = View.GONE
      }
    }

    for (view in listOf(root, overlayButton, clipboardButton)) {
      view.run {
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
    }
  }

  private val dragDiameterPx =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      DRAG_DIAMETER_DP,
      service.applicationContext.resources.displayMetrics
    )

  private val layoutParams =
    WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
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
    service.windowManager.addView(root, layoutParams)
  }

  override fun close() {
    service.windowManager.removeView(root)
  }

  private fun onClickOverlayButton() {
    if (!isDragging) {
      service.openOverlay()
    }
  }

  private fun onClickClipboardButton() {
    if (!isDragging) {
      service.startActivity(
        Intent(service, BendClipboardActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
      )
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
          service.windowManager.updateViewLayout(root, layoutParams)
        }
      }
      MotionEvent.ACTION_UP -> {
        preferences.putFloatingButtonPosition(layoutParams.x, layoutParams.y)
      }
      else -> {}
    }
  }
}
