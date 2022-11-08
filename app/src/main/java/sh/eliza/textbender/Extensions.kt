package sh.eliza.textbender

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** List of children sorted by reverse drawing order. */
val AccessibilityNodeInfo.children: List<AccessibilityNodeInfo>
  get() {
    val childCount = childCount
    val list = ArrayList<AccessibilityNodeInfo>(childCount)
    for (i in 0 until childCount) {
      getChild(i, AccessibilityNodeInfo.FLAG_PREFETCH_SIBLINGS)?.let { list.add(it) }
    }
    list.sortBy { -it.drawingOrder }
    return list
  }

val AccessibilityNodeInfo.boundsInScreen: ImmutableRect
  get() = ImmutableRect(Rect().apply { getBoundsInScreen(this) })

val View.boundsInScreen: ImmutableRect
  get() {
    val location = intArrayOf(0, 0).apply { getLocationOnScreen(this) }
    return ImmutableRect(location[0], location[1], location[0] + width, location[1] + height)
  }
