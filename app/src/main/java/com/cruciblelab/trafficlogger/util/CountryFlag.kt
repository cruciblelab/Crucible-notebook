package com.cruciblelab.trafficlogger.util

/** Converts an ISO 3166-1 alpha-2 country code (e.g. "TR") into its flag emoji. */
fun countryFlagEmoji(countryCode: String?): String? {
    if (countryCode == null || countryCode.length != 2) return null
    val upper = countryCode.uppercase()
    if (upper.any { it !in 'A'..'Z' }) return null
    val base = 0x1F1E6 // regional indicator symbol letter A
    return upper.map { char -> String(Character.toChars(base + (char - 'A'))) }
        .joinToString(separator = "")
}
