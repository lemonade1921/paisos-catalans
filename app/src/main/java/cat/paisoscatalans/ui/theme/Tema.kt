package cat.paisoscatalans.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Clar = lightColorScheme(
    primary = Color(0xFF9A4521),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF380D00),
    inversePrimary = Color(0xFFFFB59A),
    secondary = Color(0xFF77574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD3C2),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF685F12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E48A),
    onTertiaryContainer = Color(0xFF201C00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    surfaceTint = Color(0xFF9A4521),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE7),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BB),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceDim = Color(0xFFE8D6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFBEAE4),
    surfaceContainerHigh = Color(0xFFF5E4DE),
    surfaceContainerHighest = Color(0xFFEFDFD9),
)

private val Fosc = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF7B2E0B),
    onPrimaryContainer = Color(0xFFFFDBCE),
    inversePrimary = Color(0xFF9A4521),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD4C871),
    onTertiary = Color(0xFF363100),
    tertiaryContainer = Color(0xFF4F4800),
    onTertiaryContainer = Color(0xFFF1E48A),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFD9),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFD9),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BB),
    surfaceTint = Color(0xFFFFB59A),
    inverseSurface = Color(0xFFF1DFD9),
    inverseOnSurface = Color(0xFF392E2B),
    outline = Color(0xFFA08D87),
    outlineVariant = Color(0xFF53433E),
    surfaceBright = Color(0xFF423734),
    surfaceDim = Color(0xFF1A110F),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D3230),
)

private val base = Typography()

/** Tipografia expressiva: xifres grans i gruixudes per als comptadors. */
val Tipografia = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val XifraGran = TextStyle(fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)

@Composable
fun TemaPaisos(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Fosc else Clar,
        motionScheme = MotionScheme.expressive(),
        typography = Tipografia,
        content = content,
    )
}
