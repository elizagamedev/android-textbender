package sh.eliza.textbender

import android.service.quicksettings.Tile

private const val TAG = "FloatingButtonsTileService"

class FloatingButtonsTileService : TextbenderTileService() {
  override val desiredState: Int
    get() =
      if (serviceInstance === null) {
        Tile.STATE_UNAVAILABLE
      } else if (preferencesSnapshot?.floatingButtonsEnabled ?: false) {
        Tile.STATE_ACTIVE
      } else {
        Tile.STATE_INACTIVE
      }

  override fun onClick() {
    super.onClick()
    if (serviceInstance !== null) {
      preferences.putFloatingButtonEnabled(!preferences.snapshot.floatingButtonsEnabled)
    }
  }
}
