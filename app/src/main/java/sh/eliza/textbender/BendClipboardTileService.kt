package sh.eliza.textbender

import android.content.Intent
import android.service.quicksettings.TileService

class BendClipboardTileService : TileService() {
  override fun onClick() {
    super.onClick()
    startActivityAndCollapse(
      Intent(this, BendClipboardActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    )
  }
}
