package sh.eliza.textbender

class OcclusionBuffer {
  /** List of disjoint rects representing occluded areas. */
  private val buffer = mutableListOf<ImmutableRect>()

  /** Returns true if the given rect is at least partially visible. */
  fun isPartiallyVisible(rect: ImmutableRect): Boolean {
    if (buffer.any { it.contains(rect) }) {
      return false
    }
    val subrects =
      buffer.map { rect.difference(it) }.firstOrNull { !it.isEmpty() && it.first() != rect }
    if (subrects === null) {
      // It's completely disjoint from the rects in the buffer.
      return true
    }
    for (subrect in subrects) {
      if (isPartiallyVisible(subrect)) {
        return true
      }
    }
    return false
  }

  /** Occludes the area of the given rect and returns true it's at least partially visible. */
  fun add(rect: ImmutableRect): Boolean {
    if (buffer.any { it.contains(rect) }) {
      // Fully occluded by another rect.
      return false
    }
    val subrects =
      buffer.map { rect.difference(it) }.firstOrNull { !it.isEmpty() && it.first() != rect }
    if (subrects === null) {
      // It's completely disjoint from the rects in the buffer.
      buffer.add(rect)
      return true
    }
    // Add each of the subdivided rects individually.
    var isPartiallyVisible = false
    for (subrect in subrects) {
      if (add(subrect)) {
        isPartiallyVisible = true
      }
    }
    return isPartiallyVisible
  }

  fun clear() = buffer.clear()
}
