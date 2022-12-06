package sh.eliza.textbender

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ToggleOverlayButtonTileService : TileService() {
  private lateinit var preferences: TextbenderPreferences

  override fun onStartListening() {
    super.onStartListening()
    preferences = TextbenderPreferences.getInstance(applicationContext)
    updateState()
    preferences.registerOnChangeListener(this::updateState)
  }

  override fun onStopListening() {
    super.onStopListening()
    preferences.unregisterOnChangeListener(this::updateState)
  }

  override fun onClick() {
    super.onClick()
    preferences.putFloatingButtonEnabled(!preferences.snapshot.floatingButtonEnabled)
  }

  private fun updateState() {
    qsTile.state =
      if (TextbenderService.instance === null) {
        Tile.STATE_UNAVAILABLE
      } else if (preferences.snapshot.floatingButtonEnabled) {
        Tile.STATE_ACTIVE
      } else {
        Tile.STATE_INACTIVE
      }
  }
}
