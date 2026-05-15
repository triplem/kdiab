package org.javafreedom.kdiab.users.application.service

// Splits a display name into (firstName, lastName?).
// Uses the last space as the split point so "José María García" → ("José María", "García").
// Single-token names (no space) yield (displayName, null).
internal fun splitDisplayName(displayName: String): Pair<String, String?> {
    val lastSpace = displayName.lastIndexOf(' ')
    return if (lastSpace < 0) {
        displayName to null
    } else {
        displayName.substring(0, lastSpace) to displayName.substring(lastSpace + 1)
    }
}
