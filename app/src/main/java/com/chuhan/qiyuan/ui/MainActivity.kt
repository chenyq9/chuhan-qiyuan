package com.chuhan.qiyuan.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

val WoodDark = Color(0xFF5B3A1E)
val WoodMid = Color(0xFFC89B5F)
val WoodLight = Color(0xFFE8C48A)
val InkRed = Color(0xFFC0392B)
val InkBlack = Color(0xFF1F2A36)
val Gold = Color(0xFFD4A24E)

private val ChuHanScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2A1A08),
    secondary = Color(0xFFB0783C),
    background = Color(0xFF171412),
    surface = Color(0xFF241F1A),
    surfaceVariant = Color(0xFF33291F),
    onBackground = Color(0xFFEDE3D0),
    onSurface = Color(0xFFEDE3D0),
    onSurfaceVariant = Color(0xFFC9B79B),
    error = Color(0xFFE57373)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = ChuHanScheme) {
                ChuHanApp()
            }
        }
    }
}

@Composable
fun ChuHanApp(vm: GameViewModel = viewModel()) {
    when (vm.screen) {
        "game" -> GameScreen(vm)
        "settings" -> SettingsScreen(vm)
        else -> MenuScreen(vm)
    }
}