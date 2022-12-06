package sh.eliza.textbender

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService

abstract class TextbenderTileService : TileService() {
  protected lateinit var preferences: TextbenderPreferences
  protected var preferencesSnapshot: TextbenderPreferences.Snapshot
    get() = preferencesSnapshotField ?: preferences.defaults
    set(value) {
      preferencesSnapshotField = value
    }
  private var preferencesSnapshotField: TextbenderPreferences.Snapshot? = null
  protected var serviceInstance: TextbenderService? = null

  private val handler = Handler(Looper.getMainLooper())

  abstract val desiredState: Int

  override fun onCreate() {
    super.onCreate()
    preferences = TextbenderPreferences.getInstance(applicationContext)
  }

  override fun onStartListening() {
    super.onStartListening()
    if (Build.VERSION.SDK_INT >= 29) {
      qsTile.subtitle = getString(R.string.app_name)
    }

    preferences.addOnChangeListener(this::onPreferenceChanged, handler)
    TextbenderService.addOnInstanceChangedListener(this::onServiceInstanceChanged, handler)

    preferencesSnapshot = preferences.snapshot
    serviceInstance = TextbenderService.instance
    updateState()
  }

  override fun onStopListening() {
    super.onStopListening()

    TextbenderService.removeOnInstanceChangedListener(this::onServiceInstanceChanged)
    preferences.removeOnChangeListener(this::onPreferenceChanged)
  }

  private fun onPreferenceChanged(preferencesSnapshot: TextbenderPreferences.Snapshot) {
    this.preferencesSnapshot = preferencesSnapshot
    updateState()
  }

  private fun onServiceInstanceChanged(serviceInstance: TextbenderService?) {
    this.serviceInstance = serviceInstance
    updateState()
  }

  private fun updateState() {
    qsTile.run {
      state = desiredState
      updateTile()
    }
  }
}
