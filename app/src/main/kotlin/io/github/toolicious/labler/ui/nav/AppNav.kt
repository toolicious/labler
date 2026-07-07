package io.github.toolicious.labler.ui.nav

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.toolicious.labler.ui.editor.EditorScreen
import io.github.toolicious.labler.ui.history.HistoryScreen
import io.github.toolicious.labler.ui.home.HomeScreen
import io.github.toolicious.labler.ui.settings.SettingsScreen
import io.github.toolicious.labler.ui.testprint.TestPrintScreen

// Material 3 predictive-back motion (adopted from the Textary app). Values taken 1:1
// from the M3 pattern "Full-screen surface transitions": the previous page peeks out
// from under the current one while swiping back, as the current one scales down,
// slides toward the swipe edge and fades out. The gesture progress drives this live,
// because navigation-compose hooks the predictive-back gesture seekably to popExit/popEnter.
//   • Exit scaling:    100 % -> 90 %
//   • X offset:        width / 12 toward the swipe edge
//   • Exit fade:       100 % -> 0 %
//   • Enter fade:      85 % -> 100 %
//   • Easing:          cubic-bezier(0.1, 0.1, 0, 1) (system back)
//   • Duration:        300 ms back / 350 ms forward
private const val FORWARD_MS = 350
private const val BACK_MS = 300
private const val PEEK_SCALE = 0.9f
private const val PEEK_FADE_INITIAL = 0.85f
private val SystemBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(FORWARD_MS, easing = EaseOutCubic),
                initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
            ) + fadeIn(tween(FORWARD_MS / 2))
        },
        exitTransition = {
            fadeOut(tween(FORWARD_MS / 2))
        },
        popEnterTransition = {
            // Previous page fades from 0.85 to 1.0 so that it "rises up from below"
            // instead of standing there flat.
            fadeIn(
                animationSpec = tween(BACK_MS, easing = SystemBackEasing),
                initialAlpha = PEEK_FADE_INITIAL,
            )
        },
        popExitTransition = {
            // Current page scales down to 0.9, slides toward the edge and fades out,
            // all at once, so that the card visibly "lifts off".
            scaleOut(
                targetScale = PEEK_SCALE,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(BACK_MS, easing = SystemBackEasing),
            ) + slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth / 12 },
                animationSpec = tween(BACK_MS, easing = SystemBackEasing),
            ) + fadeOut(
                animationSpec = tween(BACK_MS, easing = SystemBackEasing),
            )
        },
    ) {
        composable("home") {
            HomeScreen(
                onOpenSettings = { nav.navigate("settings") },
                onOpenTemplate = { id -> nav.navigate("editor/$id") },
                onOpenHistory = { nav.navigate("history") }
            )
        }
        composable("history") {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
        composable("editor/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            EditorScreen(
                templateId = id,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenTestPrint = { nav.navigate("testprint") }
            )
        }
        composable("testprint") {
            TestPrintScreen(onOpenSettings = { nav.popBackStack() })
        }
    }
}
