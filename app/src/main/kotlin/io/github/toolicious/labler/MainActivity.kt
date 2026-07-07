package io.github.toolicious.labler

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.toolicious.labler.ui.info.wrapWithAppLanguage
import io.github.toolicious.labler.ui.nav.AppNav
import io.github.toolicious.labler.ui.theme.LablerTheme

class MainActivity : ComponentActivity() {
    // Before Android 13, apply the in-app selected language (from API 33 on the platform handles this).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapWithAppLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashStart = SystemClock.elapsedRealtime()
        enableEdgeToEdge()
        setContent {
            LablerTheme {
                AppNav()
            }
        }
        // Only hold on a real cold start (only then does the system show a splash). On
        // config changes (rotation, dark mode, language switch via recreate()) there is no
        // splash, otherwise the PreDraw hold would freeze the new frame for ~500 ms.
        if (savedInstanceState == null) holdSplashForAnimation(splashStart)
    }

    // From API 31 on, hold the system splash briefly so the icon animation
    // (label emerging from the printer) plays through visibly. Older versions without a hold.
    private fun holdSplashForAnimation(start: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (SystemClock.elapsedRealtime() - start < SPLASH_HOLD_MS) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Re-arm the reconnect on every resume (onResume, not onStart, so it also fires after the
        // permission dialog, which only pauses the Activity). A just-granted permission then takes
        // effect without a restart. It is a no-op when already connected/connecting or already waiting.
        (application as App).container.printerManager.startBackgroundReconnect()
    }

    private companion object {
        // Covers the start offset (150 ms) + the extend (320 ms) plus some buffer,
        // so the whole motion is visible before the splash fades out.
        const val SPLASH_HOLD_MS = 500L
    }
}
