package sh.eliza.textbender

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ToggleOverlayButtonTileService : TileService() {
  override fun onStartListening() {
    super.onStartListening()
    updateState()
    TextbenderPreferences.registerOnChangeListener(this, this::updateState)
  }

  override fun onStopListening() {
    super.onStopListening()
    TextbenderPreferences.unregisterOnChangeListener(this, this::updateState)
  }

  override fun onClick() {
    super.onClick()
    TextbenderPreferences.putFloatingButtonEnabled(
      this,
      !TextbenderPreferences.createFromContext(this).floatingButtonEnabled
    )
  }

  private fun updateState() {
    qsTile.state =
      if (TextbenderService.instance === null) {
        Tile.STATE_UNAVAILABLE
      } else if (TextbenderPreferences.createFromContext(this).floatingButtonEnabled) {
        Tile.STATE_ACTIVE
      } else {
        Tile.STATE_INACTIVE
      }
  }
}
