package co.ast.campo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.ast.campo.data.ThemeMode
import co.ast.campo.ui.AstTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AstApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val light = settings.theme == ThemeMode.LIGHT_HC
            // Barras del sistema transparentes con íconos claros u oscuros según el tema.
            androidx.compose.runtime.LaunchedEffect(light) {
                enableEdgeToEdge(
                    statusBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT),
                )
            }
            AstTheme(settings.theme) { AstNavHost() }
        }
    }
}
