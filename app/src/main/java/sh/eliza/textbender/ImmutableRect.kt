package sh.eliza.textbender

import android.graphics.Rect
import android.graphics.RectF

class ImmutableRect(private val rect: Rect) {
  constructor(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
  ) : this(Rect(left, top, right, bottom)) {}

  constructor(rectf: RectF) : this(Rect().apply { rectf.round(this) }) {}

  val left = rect.left
  val top = rect.top
  val right = rect.right
  val bottom = rect.bottom
  val width = rect.width()
  val height = rect.height()

  val isEmpty: Boolean
    get() = rect.isEmpty()

  fun intersects(other: ImmutableRect) = Rect.intersects(rect, other.rect)

  fun intersect(other: ImmutableRect) =
    if (intersects(other)) {
      ImmutableRect(Rect().apply { @Suppress("CheckResult") setIntersect(rect, other.rect) })
    } else {
      null
    }

  fun union(other: ImmutableRect) = Rect(rect).apply { union(other.rect) }

  fun difference(other: ImmutableRect): List<ImmutableRect> {
    if (other.width == 0 || other.height == 0 || other.contains(this)) {
      return listOf()
    }
    return listOf(
        // Greedy left.
        ImmutableRect(left, top, other.left, bottom),
        // Greedy right.
        ImmutableRect(other.right, top, right, bottom),
        // Shy top.
        ImmutableRect(other.left, top, other.right, other.top),
        // Shy bottom.
        ImmutableRect(other.left, other.bottom, other.right, bottom),
      )
      .filter { !it.isEmpty && contains(it) }
  }

  fun offset(dx: Int, dy: Int) = ImmutableRect(Rect(rect).apply { offset(dx, dy) })

  fun contains(other: ImmutableRect) = rect.contains(other.rect)

  fun inset(dxy: Int) = ImmutableRect(Rect(rect).apply { inset(dxy, dxy) })

  override fun equals(other: Any?) = rect.equals((other as? ImmutableRect)?.rect)

  override fun hashCode() = rect.hashCode()

  override fun toString() = rect.toString()
}
