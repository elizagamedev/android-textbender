package sh.eliza.textbender

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

private const val TAG = "FloatingButtonsTileService"

class FloatingButtonsTileService : TileService() {
  private lateinit var preferences: TextbenderPreferences
  private var preferencesSnapshot: TextbenderPreferences.Snapshot? = null
  private var serviceInstance: TextbenderService? = null
  private val handler = Handler(Looper.getMainLooper())

  override fun onCreate() {
    super.onCreate()
    preferences = TextbenderPreferences.getInstance(applicationContext)
  }

  override fun onStartListening() {
    super.onStartListening()
    qsTile.subtitle = getString(R.string.app_name)

    preferences.registerOnChangeListener(this::onPreferenceChanged)
    TextbenderService.addOnInstanceChangedListener(this::onServiceInstanceChanged, handler)

    preferencesSnapshot = preferences.snapshot
    serviceInstance = TextbenderService.instance
    updateState()
  }

  override fun onStopListening() {
    super.onStopListening()

    TextbenderService.removeOnInstanceChangedListener(this::onServiceInstanceChanged)
    preferences.unregisterOnChangeListener(this::onPreferenceChanged)
  }

  override fun onClick() {
    super.onClick()
    if (serviceInstance !== null) {
      preferences.putFloatingButtonEnabled(!preferences.snapshot.floatingButtonEnabled)
    }
  }

  private fun onPreferenceChanged() {
    val preferencesSnapshot = preferences.snapshot
    handler.post {
      this.preferencesSnapshot = preferencesSnapshot
      updateState()
    }
  }

  private fun onServiceInstanceChanged(serviceInstance: TextbenderService?) {
    this.serviceInstance = serviceInstance
    updateState()
  }

  private fun updateState() {
    qsTile.run {
      state =
        if (serviceInstance === null) {
          Tile.STATE_UNAVAILABLE
        } else if (preferencesSnapshot?.floatingButtonEnabled ?: false) {
          Tile.STATE_ACTIVE
        } else {
          Tile.STATE_INACTIVE
        }
      updateTile()
    }
  }
}
