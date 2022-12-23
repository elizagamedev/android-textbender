package sh.eliza.textbender

import android.content.Intent
import android.service.quicksettings.Tile
import android.widget.Toast

class ActivateOverlayTileService : TextbenderTileService() {
  private val toaster = Toaster(this)

  override val desiredState: Int
    get() =
      if (serviceInstance === null) {
        Tile.STATE_UNAVAILABLE
      } else {
        Tile.STATE_INACTIVE
      }

  override fun onClick() {
    super.onClick()
    val serviceInstance = serviceInstance
    if (serviceInstance !== null) {
      serviceInstance.openOverlay(500L, showToast = false)
      startActivityAndCollapse(
        Intent(this, DummyActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
      )
    } else {
      toaster.show(getString(R.string.could_not_access_accessibility_service), Toast.LENGTH_SHORT)
    }
  }
}
