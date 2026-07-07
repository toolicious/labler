package io.github.toolicious.labler.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.toolicious.labler.ble.BlePermissions

/**
 * Returns a runner that executes an action only after the Bluetooth
 * permissions have been granted (requesting them if needed).
 */
@Composable
fun rememberBlePermissionRunner(): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val action = pending
        pending = null
        if (grants.values.all { it }) action?.invoke()
    }
    return { action ->
        if (BlePermissions.allGranted(context)) {
            action()
        } else {
            pending = action
            launcher.launch(BlePermissions.required())
        }
    }
}

/** Live Bluetooth-permission state plus a one-tap "grant" action for a status/warning UI. */
class BlePermissionState(val granted: Boolean, val request: () -> Unit)

/**
 * Tracks whether the Bluetooth permissions are granted, re-checked whenever the screen resumes so a
 * grant made in the system settings is picked up. [request] shows the permission dialog; if the
 * permission stays denied (declined again, or permanently denied so no dialog appears) it opens the
 * app settings, where the user can still enable it.
 */
@Composable
fun rememberBlePermissionState(): BlePermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BlePermissions.allGranted(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = BlePermissions.allGranted(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Trust the actual permission state, not the result map (an empty/cancelled map would make
        // `all { it }` vacuously true and wrongly report granted).
        granted = BlePermissions.allGranted(context)
        if (!granted) {
            // Only when the system will no longer show the dialog (permanently denied) do we send the
            // user to the app settings. After a soft denial the in-app warning stays and a re-tap
            // re-prompts, so a single accidental "Deny" does not eject the user from the app.
            val activity = context.findActivity()
            val canAskAgain = activity != null && BlePermissions.required().any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            if (!canAskAgain) openAppSettings(context)
        }
    }
    val request: () -> Unit = {
        if (BlePermissions.allGranted(context)) granted = true
        else launcher.launch(BlePermissions.required())
    }
    return BlePermissionState(granted, request)
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Opens this app's system settings page (used when a permission is permanently denied). */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }
}
