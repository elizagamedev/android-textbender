package sh.eliza.textbender

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class TextbenderAccessibilityService : AccessibilityService() {
  override fun onServiceConnected() {}

  override fun onAccessibilityEvent(event: AccessibilityEvent) {}

  override fun onInterrupt() {}

  override fun onUnbind(intent: Intent): Boolean {
    return super.onUnbind(intent)
  }
}
