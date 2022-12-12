package sh.eliza.textbender

import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.min

private const val TAG = "Extensions"

/** List of children sorted by reverse drawing order. */
val AccessibilityNodeInfo.children: List<AccessibilityNodeInfo>
  get() {
    val childCount = childCount
    val list = ArrayList<AccessibilityNodeInfo>(childCount)
    for (i in 0 until childCount) {
      getChild(i)?.let { list.add(it) }
    }
    list.sortBy { it.drawingOrder }
    list.reverse()
    return list
  }

val AccessibilityNodeInfo.boundsInScreen: ImmutableRect
  get() = ImmutableRect(Rect().apply { getBoundsInScreen(this) })

val AccessibilityNodeInfo.textSizeInPx: Float?
  get() =
    if (Build.VERSION.SDK_INT >= 30) {
      refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY, Bundle())
      extraRenderingInfo?.textSizeInPx
    } else {
      null
    }

val AccessibilityNodeInfo.textBounds: ImmutableRect?
  get() {
    if (Build.VERSION.SDK_INT < 31) {
      return null
    }
    refreshWithExtraData(
      AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
      Bundle().apply {
        putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
        putInt(
          AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
          min(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH, text.length)
        )
      }
    )
    val array =
      if (Build.VERSION.SDK_INT >= 33) {
        extras.getParcelableArray(
          AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
          RectF::class.java
        )
      } else {
        @Suppress("DEPRECATION")
        extras
          .getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
          ?.map { it as RectF }
          ?.toTypedArray()
      }
    if (array === null || array.all { it === null }) {
      return null
    }
    val result = RectF()
    for (rect in array) {
      if (rect !== null) {
        result.union(rect)
      }
    }
    return ImmutableRect(result)
  }

fun AccessibilityNodeInfo.find(
  predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
  if (predicate(this)) {
    return this
  }
  val childCount = childCount
  for (i in 0 until childCount) {
    val result = getChild(i)?.find(predicate)
    if (result !== null) {
      return result
    }
  }
  return null
}

val View.boundsInScreen: ImmutableRect
  get() {
    val location = intArrayOf(0, 0).apply { getLocationOnScreen(this) }
    return ImmutableRect(location[0], location[1], location[0] + width, location[1] + height)
  }

/* Debug functions */
fun AccessibilityNodeInfo.dump(prefix: String) {
  val myPrefix = "$prefix/$className"
  if (text !== null) {
    Log.d(TAG, "$myPrefix: $text")
  }
  for (child in children) {
    child.dump(myPrefix)
  }
}

fun AccessibilityNodeInfo.grep(needle: String) {
  val text = text
  if (text !== null && text.contains(needle)) {
    Log.d(TAG, "found: text = $text viewIdResourceName = $viewIdResourceName")
  }
  for (child in children) {
    child.grep(needle)
  }
}

fun AccessibilityWindowInfo.dump() {
  val root = root
  if (root !== null) {
    root.dump("$title -- ")
  }
}
