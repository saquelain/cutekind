package app.cutekind.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Rose = Color(0xFFE8507A)
val Ink = Color(0xFF3B2A33)

private val Light = lightColorScheme(
    primary = Rose, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCE6), onPrimaryContainer = Color(0xFF5A1230),
    secondary = Color(0xFF8A5A6B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9DC), onSecondaryContainer = Color(0xFF4A2B1A),
    background = Color(0xFFFFF7F2), onBackground = Ink,
    surface = Color(0xFFFFF7F2), onSurface = Ink,
    surfaceVariant = Color(0xFFF6E6E4), onSurfaceVariant = Color(0xFF6E5560),
    surfaceContainer = Color(0xFFFFEFEA), surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFF8E4E2),
    outline = Color(0xFFCFB3B9), outlineVariant = Color(0xFFEBD5D6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8FAE), onPrimary = Color(0xFF4A0F27),
    primaryContainer = Color(0xFF6B2340), onPrimaryContainer = Color(0xFFFFDCE6),
    secondary = Color(0xFFE5BBC8), onSecondary = Color(0xFF43273A),
    secondaryContainer = Color(0xFF4A3340), onSecondaryContainer = Color(0xFFFFE9DC),
    background = Color(0xFF1B1418), onBackground = Color(0xFFF5E6EA),
    surface = Color(0xFF1B1418), onSurface = Color(0xFFF5E6EA),
    surfaceVariant = Color(0xFF3A2C33), onSurfaceVariant = Color(0xFFD3BCC4),
    surfaceContainer = Color(0xFF261C21), surfaceContainerHigh = Color(0xFF30242A),
    surfaceContainerLow = Color(0xFF241A1F), surfaceContainerHighest = Color(0xFF3A2C33),
    outline = Color(0xFF7D6670), outlineVariant = Color(0xFF4A3A42),
)

@Composable
fun CuteKindTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}

val PENS = listOf(0xFF3B2A33, 0xFFE8507A, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFFFFFFF).map { it.toInt() }
val PAPERS = listOf(0xFFFFFDF8, 0xFFFFE1EA, 0xFFDDF1FF, 0xFFE0F5E6, 0xFF1F1B2E).map { it.toInt() }
val BRUSHES = listOf(0.008f, 0.018f, 0.04f)
