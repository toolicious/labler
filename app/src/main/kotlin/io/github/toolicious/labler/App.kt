package io.github.toolicious.labler

import android.app.Application
import android.content.Context
import io.github.toolicious.labler.render.FontRegistry
import io.github.toolicious.labler.ui.editor.lastSymbolTab
import io.github.toolicious.labler.ui.info.wrapWithAppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var container: AppContainer
        private set

    // Before Android 13, also apply the selected app language to the application context,
    // so that getString() in the data/BLE layer uses the app language (from API 33: platform).
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(wrapWithAppLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        FontRegistry.init(this)
        // Load the most recently used symbol/emoji tab from the settings into the cache.
        container.applicationScope.launch {
            lastSymbolTab = container.settings.lastSymbolTab.first()
        }
    }
}
