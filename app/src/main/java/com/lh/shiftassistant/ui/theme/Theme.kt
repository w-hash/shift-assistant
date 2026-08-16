package com.lh.shiftassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    // 卡片容器色:与背景(0xFFFBF8FF)略有差异,保证卡片圆角轮廓可见
    surfaceContainer = Color(0xFFF3EDF7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF002F66),
    primaryContainer = Color(0xFF0F2E5C),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF80D5DA),
    onSecondary = Color(0xFF00373A),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFF9CF1F6),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE1E2E7),
    surface = Color(0xFF111316),
    onSurface = Color(0xFFE1E2E7),
    surfaceVariant = Color(0xFF44474C),
    onSurfaceVariant = Color(0xFFC4C6CB),
    outline = Color(0xFF8E9096),
    // 卡片容器色:深色下比背景(0xFF111316)略亮,保证卡片圆角轮廓可见
    surfaceContainer = Color(0xFF1D1D20)
)

@Composable
fun ShiftAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
