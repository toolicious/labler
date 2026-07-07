package io.github.toolicious.labler.ui.testprint

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.toolicious.labler.App

/** Provides only the printer status for the status chip; printing runs through the PrintSheet. */
class TestPrintViewModel(app: Application) : AndroidViewModel(app) {
    val printerState = (app as App).container.printerManager.state
}
