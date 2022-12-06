package sh.eliza.textbender

import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.Toast

class ActivateOverlayTileService : TileService() {
  override fun onClick() {
    super.onClick()
    val service = TextbenderService.instance
    if (service !== null) {
      service.openOverlay(500L)
      startActivityAndCollapse(
        Intent(this, DummyActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
      )
    } else {
      Toast.makeText(
          this,
          getString(R.string.could_not_access_accessibility_service),
          Toast.LENGTH_LONG
        )
        .show()
    }
  }
}
