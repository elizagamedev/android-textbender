package sh.eliza.textbender

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BendClipboardTileService : TileService() {
  private lateinit var preferences: TextbenderPreferences
  private var preferencesSnapshot: TextbenderPreferences.Snapshot? = null
  private val handler = Handler(Looper.getMainLooper())

  override fun onCreate() {
    super.onCreate()
    preferences = TextbenderPreferences.getInstance(applicationContext)
  }

  override fun onStartListening() {
    super.onStartListening()
    qsTile.subtitle = getString(R.string.app_name)
    preferences.registerOnChangeListener(this::onPreferenceChanged)
    preferencesSnapshot = preferences.snapshot
    updateState()
  }

  override fun onClick() {
    super.onClick()
    startActivityAndCollapse(
      Intent(this, BendClipboardActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    )
  }

  private fun onPreferenceChanged() {
    val preferencesSnapshot = preferences.snapshot
    handler.post {
      this.preferencesSnapshot = preferencesSnapshot
      updateState()
    }
  }

  private fun updateState() {
    qsTile.run {
      val clipboardDestination =
        preferencesSnapshot?.clipboardDestination ?: TextbenderPreferences.Destination.DISABLED
      state =
        if (clipboardDestination != TextbenderPreferences.Destination.DISABLED) {
          Tile.STATE_INACTIVE
        } else {
          Tile.STATE_UNAVAILABLE
        }
      updateTile()
    }
  }
}
