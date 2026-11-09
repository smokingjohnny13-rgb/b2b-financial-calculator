package com.example.util

/**
 * Universal safe parser supporting localized decimal commas (e.g. "12,5" -> 12.5)
 * and integer conversions without throwing exceptions.
 */
fun String?.toCleanDouble(): Double? {
    if (this.isNullOrBlank()) return null
    return this.replace(',', '.').trim().toDoubleOrNull()
}

fun String?.toCleanDouble(default: Double): Double = this.toCleanDouble() ?: default

fun String?.toCleanInt(): Int? {
    if (this.isNullOrBlank()) return null
    val sanitized = this.replace(',', '.').trim()
    return sanitized.toIntOrNull() ?: sanitized.toDoubleOrNull()?.toInt()
}

fun String?.toCleanInt(default: Int): Int = this.toCleanInt() ?: default
