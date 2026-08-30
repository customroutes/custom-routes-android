package io.github.customroutes.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RouteColors = darkColorScheme(
    primary = Color(0xFFF1C75B),
    onPrimary = Color(0xFF292000),
    secondary = Color(0xFF8FC9A3),
    background = Color(0xFF101713),
    onBackground = Color(0xFFF0F4EE),
    surface = Color(0xFF18221D),
    onSurface = Color(0xFFF0F4EE),
    surfaceVariant = Color(0xFF26342D),
    onSurfaceVariant = Color(0xFFC1CEC6),
    error = Color(0xFFFFB4AB),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RouteColors,
        content = content,
    )
}
