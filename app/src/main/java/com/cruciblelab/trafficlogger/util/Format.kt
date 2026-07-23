package com.cruciblelab.trafficlogger.util

import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent)
    return String.format("%.1f %s", value, units[exponent - 1])
}

fun formatTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
