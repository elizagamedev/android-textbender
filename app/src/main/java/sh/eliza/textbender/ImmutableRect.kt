package sh.eliza.textbender

import android.graphics.Rect

class ImmutableRect(private val rect: Rect) {
  constructor(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
  ) : this(Rect(left, top, right, bottom)) {}

  val left = rect.left
  val top = rect.top
  val right = rect.right
  val bottom = rect.bottom
  val width = rect.width()
  val height = rect.height()

  fun intersects(other: ImmutableRect) = Rect.intersects(rect, other.rect)

  fun intersect(other: ImmutableRect) =
    if (intersects(other)) {
      ImmutableRect(Rect().apply { setIntersect(rect, other.rect) })
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
      .filter { it.height > 0 && it.width > 0 && contains(it) }
  }

  fun offset(dx: Int, dy: Int) = ImmutableRect(Rect(rect).apply { offset(dx, dy) })

  fun contains(other: ImmutableRect) = rect.contains(other.rect)

  override fun equals(other: Any?) = rect.equals((other as? ImmutableRect)?.rect)

  override fun hashCode() = rect.hashCode()

  override fun toString() = rect.toString()
}
