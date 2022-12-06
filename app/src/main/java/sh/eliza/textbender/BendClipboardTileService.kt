package sh.eliza.textbender

import android.content.Intent
import android.service.quicksettings.Tile

class BendClipboardTileService : TextbenderTileService() {
  override val desiredState: Int
    get() {
      val clipboardDestination =
        preferencesSnapshot?.clipboardDestination ?: TextbenderPreferences.Destination.DISABLED
      return if (clipboardDestination != TextbenderPreferences.Destination.DISABLED) {
        Tile.STATE_INACTIVE
      } else {
        Tile.STATE_UNAVAILABLE
      }
    }

  override fun onClick() {
    super.onClick()
    startActivityAndCollapse(
      Intent(this, BendClipboardActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    )
  }
}
