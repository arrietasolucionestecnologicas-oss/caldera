package co.ast.campo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import co.ast.campo.data.ThemeMode

/** Paleta propuesta para A.S.T. (no es un Brand Kit oficial): azul marino, naranja de acción, cian de datos. */
object AstColors {
    val Navy950 = Color(0xFF07101F)
    val Navy900 = Color(0xFF0B1A33)
    val Navy850 = Color(0xFF0E2040)
    val Navy800 = Color(0xFF112544)
    val Navy750 = Color(0xFF15305A)
    val Navy700 = Color(0xFF1A3A66)
    val Navy600 = Color(0xFF264E82)
    val Orange = Color(0xFFF58220)
    val Cyan = Color(0xFF2BD4F0)
    val Text = Color(0xFFE8EEF7)
    val TextDim = Color(0xFF9FB0C8)
    val Warn = Color(0xFFFFC857)
    val Error = Color(0xFFFF7A70)
}

private val DarkScheme = darkColorScheme(
    primary = AstColors.Orange,
    onPrimary = AstColors.Navy950,
    primaryContainer = Color(0xFF7A3A08),
    onPrimaryContainer = Color(0xFFFFE2CC),
    secondary = AstColors.Cyan,
    onSecondary = AstColors.Navy950,
    secondaryContainer = Color(0xFF0E4B5A),
    onSecondaryContainer = Color(0xFFCFF6FF),
    tertiary = AstColors.Cyan,
    onTertiary = AstColors.Navy950,
    background = AstColors.Navy900,
    onBackground = AstColors.Text,
    surface = AstColors.Navy900,
    onSurface = AstColors.Text,
    surfaceVariant = AstColors.Navy800,
    onSurfaceVariant = AstColors.TextDim,
    surfaceTint = AstColors.Orange,
    surfaceContainerLowest = AstColors.Navy950,
    surfaceContainerLow = AstColors.Navy850,
    surfaceContainer = AstColors.Navy800,
    surfaceContainerHigh = AstColors.Navy750,
    surfaceContainerHighest = AstColors.Navy700,
    outline = AstColors.Navy600,
    outlineVariant = AstColors.Navy700,
    error = AstColors.Error,
    onError = AstColors.Navy950,
)

/** Tema claro de alto contraste para usar al sol. */
private val LightHcScheme = lightColorScheme(
    primary = Color(0xFFB04A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC4),
    onPrimaryContainer = Color(0xFF3A1500),
    secondary = Color(0xFF00566A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F1FB),
    onSecondaryContainer = Color(0xFF001F27),
    tertiary = Color(0xFF00566A),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF07101F),
    surface = Color.White,
    onSurface = Color(0xFF07101F),
    surfaceVariant = Color(0xFFE3EAF4),
    onSurfaceVariant = Color(0xFF1F2E48),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5FA),
    surfaceContainer = Color(0xFFE8EEF6),
    surfaceContainerHigh = Color(0xFFDDE5F0),
    surfaceContainerHighest = Color(0xFFD0DAE8),
    outline = Color(0xFF3B4A66),
    outlineVariant = Color(0xFF7C8AA3),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val AstTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    )
}

@Composable
fun AstTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode == ThemeMode.LIGHT_HC) LightHcScheme else DarkScheme,
        typography = AstTypography,
        content = content,
    )
}
