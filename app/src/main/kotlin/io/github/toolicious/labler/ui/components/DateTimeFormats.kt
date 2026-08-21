package io.github.toolicious.labler.ui.components

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import io.github.toolicious.labler.model.Placeholders
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.text.format.DateFormat as SystemDateFormat

/*
 * Dates and times in the format the device itself uses. Everything that shows or prints a stamp
 * goes through here, so the preview, the printed label and the history list cannot drift apart.
 */

/**
 * The locale of the device, which is what a date has to follow. Deliberately not the one the
 * interface runs in: Android keeps the two apart, an in-app language choice changes the words and
 * leaves the regional conventions alone, so a phone set to German prints a German date even while
 * the app itself speaks English.
 */
fun systemLocale(context: Context): Locale {
    if (Build.VERSION.SDK_INT >= 33) {
        val locales = context.getSystemService(LocaleManager::class.java)?.systemLocales
        if (locales != null && !locales.isEmpty) return locales[0]
    }
    // Below that, and as a fallback: the framework resources carry the device configuration, which
    // no per-app language can override, unlike Locale.getDefault().
    return Resources.getSystem().configuration.locales[0]
}

/**
 * The locale's own numeric date, for example 21.08.2026 or 8/21/2026. Built from a skeleton rather
 * than DateFormat.SHORT, which shortens the year to two digits in a number of locales.
 */
fun appDateFormat(context: Context): DateFormat = systemLocale(context).let { locale ->
    SimpleDateFormat(SystemDateFormat.getBestDateTimePattern(locale, "yMd"), locale)
}

/** The locale's own time, honouring the 12 or 24 hour setting of the device. */
fun appTimeFormat(context: Context): DateFormat = systemLocale(context).let { locale ->
    SimpleDateFormat(SystemDateFormat.getBestDateTimePattern(locale, hourSkeleton(context)), locale)
}

/** Date and time together, for lists that state when something happened. */
fun appDateTimeFormat(context: Context): DateFormat = systemLocale(context).let { locale ->
    val skeleton = "yMd" + hourSkeleton(context)
    SimpleDateFormat(SystemDateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

private fun hourSkeleton(context: Context) = if (SystemDateFormat.is24HourFormat(context)) "Hm" else "hm"

/**
 * Placeholder values for a print at [now]. Built at the moment of printing rather than kept around,
 * so a sheet that has been open for a while still stamps the current time.
 */
fun placeholderContext(
    context: Context,
    counter: Int,
    answers: Map<String, String>,
    now: Date = Date(),
): Placeholders.Context = Placeholders.Context(
    dateText = appDateFormat(context).format(now),
    timeText = appTimeFormat(context).format(now),
    counter = counter,
    answers = answers,
    now = now,
    locale = systemLocale(context),
)
