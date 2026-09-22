package com.example.ui.theme

import androidx.compose.ui.graphics.Color

object SplitColors {
    val Background = Color(0xFFF8F9FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF1F3F5)
    val Border = Color(0xFFE9ECEF)
    val TextPrimary = Color(0xFF1E293B)
    val TextSecondary = Color(0xFF64748B)
    val TextMuted = Color(0xFF94A3B8)

    val Primary = Color(0xFF2563EB)
    val PrimaryLight = Color(0xFFEFF6FF)
    val PrimaryDark = Color(0xFF1D4ED8)

    val Success = Color(0xFF16A34A)
    val SuccessLight = Color(0xFFF0FDF4)
    val Warning = Color(0xFFD97706)
    val WarningLight = Color(0xFFFFFBEB)

    // Palette for assigning distinct vibrant colors to people
    val avatarColors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Emerald
        Color(0xFF8B5CF6), // Purple
        Color(0xFFF59E0B), // Amber
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF97316), // Orange
        Color(0xFF6366F1), // Indigo
        Color(0xFF14B8A6), // Teal
        Color(0xFFEF4444)  // Rose
    )

    fun getAvatarColor(name: String): Color {
        if (name.isEmpty()) return avatarColors[0]
        val hash = kotlin.math.abs(name.hashCode())
        return avatarColors[hash % avatarColors.size]
    }
}
