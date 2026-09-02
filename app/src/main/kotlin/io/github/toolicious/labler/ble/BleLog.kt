package io.github.toolicious.labler.ble

import android.util.Log
import io.github.toolicious.labler.BuildConfig

/** Debug-only BLE diagnostics (tag LaBLEr). Never printed in a release build. */
internal fun bleLog(message: String) {
    if (BuildConfig.DEBUG) Log.i("LaBLEr", message)
}
