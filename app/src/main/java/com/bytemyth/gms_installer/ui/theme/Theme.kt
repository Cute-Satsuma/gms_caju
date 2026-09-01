package com.bytemyth.gms_installer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 启动图标拼图四色：蓝 / 红 / 黄 / 绿。
 * 页面内用作分区点缀、状态色与细条装饰。
 */
@Immutable
data class IconPalette(
    val blue: Color,
    val red: Color,
    val yellow: Color,
    val green: Color,
    val blueSoft: Color,
    val redSoft: Color,
    val yellowSoft: Color,
    val greenSoft: Color,
    val canvas: Color,
) {
    val accents: List<Color> get() = listOf(blue, red, yellow, green)
    val softs: List<Color> get() = listOf(blueSoft, redSoft, yellowSoft, greenSoft)

    fun accent(index: Int): Color = accents[index.mod(4)]
    fun soft(index: Int): Color = softs[index.mod(4)]
}

val LocalIconPalette = staticCompositionLocalOf {
    IconPalette(
        blue = Color(0xFF4285F4),
        red = Color(0xFFEA4335),
        yellow = Color(0xFFFBBC05),
        green = Color(0xFF34A853),
        blueSoft = Color(0xFFD2E3FC),
        redSoft = Color(0xFFFAD2CF),
        yellowSoft = Color(0xFFFEEFC3),
        greenSoft = Color(0xFFCEEAD6),
        canvas = Color(0xFFA6D1FC),
    )
}

private val LightPalette = IconPalette(
    blue = Color(0xFF4285F4),
    red = Color(0xFFEA4335),
    yellow = Color(0xFFE37400), // 深一点，浅色底上更清晰
    green = Color(0xFF34A853),
    blueSoft = Color(0xFFD2E3FC),
    redSoft = Color(0xFFFCE8E6),
    yellowSoft = Color(0xFFFEF7E0),
    greenSoft = Color(0xFFE6F4EA),
    canvas = Color(0xFFE8F0FE),
)

private val DarkPalette = IconPalette(
    blue = Color(0xFF8AB4F8),
    red = Color(0xFFF28B82),
    yellow = Color(0xFFFDD663),
    green = Color(0xFF81C995),
    blueSoft = Color(0xFF1A3A6B),
    redSoft = Color(0xFF5C221C),
    yellowSoft = Color(0xFF5C4300),
    greenSoft = Color(0xFF1E4D2C),
    canvas = Color(0xFF1A2A40),
)

private val LightColors = lightColorScheme(
    primary = LightPalette.blue,
    onPrimary = Color.White,
    primaryContainer = LightPalette.blueSoft,
    onPrimaryContainer = Color(0xFF0B3D91),
    secondary = LightPalette.green,
    onSecondary = Color.White,
    secondaryContainer = LightPalette.greenSoft,
    onSecondaryContainer = Color(0xFF0B3B1A),
    tertiary = LightPalette.yellow,
    onTertiary = Color.White,
    tertiaryContainer = LightPalette.yellowSoft,
    onTertiaryContainer = Color(0xFF5C2E00),
    error = LightPalette.red,
    onError = Color.White,
    errorContainer = LightPalette.redSoft,
    onErrorContainer = Color(0xFF5C1510),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = LightPalette.canvas,
    onSurfaceVariant = Color(0xFF5A6169),
    outline = Color(0xFF8A9199),
    outlineVariant = Color(0xFFD0D7E0),
)

private val DarkColors = darkColorScheme(
    primary = DarkPalette.blue,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = DarkPalette.blueSoft,
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = DarkPalette.green,
    onSecondary = Color(0xFF0B3B1A),
    secondaryContainer = DarkPalette.greenSoft,
    onSecondaryContainer = Color(0xFFD6F0DE),
    tertiary = DarkPalette.yellow,
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = DarkPalette.yellowSoft,
    onTertiaryContainer = Color(0xFFFFE8CC),
    error = DarkPalette.red,
    onError = Color(0xFF3C0A07),
    errorContainer = DarkPalette.redSoft,
    onErrorContainer = Color(0xFFFCE8E6),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E6EB),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE2E6EB),
    surfaceVariant = DarkPalette.canvas,
    onSurfaceVariant = Color(0xFFB0B8C2),
    outline = Color(0xFF8A9199),
    outlineVariant = Color(0xFF3A414A),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun GmsInstallerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalIconPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}

val MaterialTheme.iconPalette: IconPalette
    @Composable
    get() = LocalIconPalette.current
