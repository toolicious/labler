package io.github.toolicious.labler.ui.info

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.toolicious.labler.R
import java.util.Locale

private const val PREFS = "labler_prefs"
private const val KEY_LANG = "app_language"

/**
 * In-app languages. The tags must match the values-* resource qualifiers.
 * Labels are the native names of the languages. Model: Compressed.
 */
val APP_LANGUAGES = listOf(
    "de" to "Deutsch",
    "en" to "English",
    "fr" to "Français",
    "es" to "Español",
    "it" to "Italiano",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "pt-BR" to "Português",
    "ru" to "Русский",
    "tr" to "Türkçe",
    "uk" to "Українська",
    "zh-CN" to "简体中文",
    "ja" to "日本語",
)

/** Only before Android 13: apply the stored app language to the base context. */
fun wrapWithAppLanguage(base: Context): Context {
    if (Build.VERSION.SDK_INT >= 33) return base
    val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LANG, null) ?: return base
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

/** The explicitly chosen language tag, or null when following the system language. */
fun currentAppLanguageTag(context: Context): String? =
    if (Build.VERSION.SDK_INT >= 33) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) null else locales.get(0).toLanguageTag()
    } else {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, null)
    }

/**
 * Applies the app language immediately; null = follow the system language. From API 33 on, the
 * platform stores the choice and restarts the activity itself, below that a pref plus manual restart.
 */
fun setAppLanguage(context: Context, tag: String?) {
    if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    } else {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (tag == null) editor.remove(KEY_LANG) else editor.putString(KEY_LANG, tag)
        editor.apply()
        // Update the application resources immediately so that getString in the data/BLE layer
        // uses the new language: attachBaseContext runs only once per process, and
        // recreate() only refreshes the activity, not the application context.
        applyAppLocale(context.applicationContext, tag)
        (context as? Activity)?.recreate()
    }
}

/** Updates the locale of the application resources in place (only needed before API 33). */
@Suppress("DEPRECATION")
private fun applyAppLocale(appContext: Context, tag: String?) {
    val locale = if (tag != null) Locale.forLanguageTag(tag)
        else Resources.getSystem().configuration.locales.get(0)
    Locale.setDefault(locale)
    val res = appContext.resources
    val config = Configuration(res.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    res.updateConfiguration(config, res.displayMetrics)
}

/** Two-letter abbreviation of the current UI language, for the badge in the info dialog. */
@Composable
fun currentLanguageBadge(): String =
    LocalConfiguration.current.locales.get(0).language.uppercase(Locale.US)

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Language selection from the info dialog: "System" at the top, then every available translation;
 * the choice takes effect immediately. Model: Compressed.
 */
@Composable
fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = currentAppLanguageTag(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_language_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LanguageRow(
                    label = stringResource(R.string.app_language_system),
                    selected = current == null,
                    onClick = { setAppLanguage(context, null); onDismiss() }
                )
                val uiLocale = LocalConfiguration.current.locales.get(0)
                APP_LANGUAGES.forEach { (tag, name) ->
                    // Native name plus, in parentheses, the name in the current app language,
                    // omitted when both are the same.
                    val localized = Locale.forLanguageTag(tag).getDisplayLanguage(uiLocale)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(uiLocale) else it.toString() }
                    val label = if (localized.isNotBlank() && !localized.equals(name, ignoreCase = true)) {
                        "$name ($localized)"
                    } else {
                        name
                    }
                    LanguageRow(
                        label = label,
                        selected = current.equals(tag, ignoreCase = true),
                        onClick = { setAppLanguage(context, tag); onDismiss() }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
