package co.ast.campo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import co.ast.campo.data.ThemeMode
import co.ast.campo.media.LocationFix
import co.ast.campo.ui.AstTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Arranca la app completa en la JVM (Robolectric) y recorre el flujo principal:
 * crear proyecto, crear subcarpeta con plantilla, abrir captura y abrir Ajustes.
 * Sirve para detectar cierres al abrir, errores de navegación o de recursos que la compilación no ve.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/test-artifacts").apply { mkdirs() }

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<AstApp>()
        app.trackerOverride = FakeLocation(LocationFix(10.96854, -74.78132, 8f, SystemClock.elapsedRealtime()))
        rule.setContent { AstTheme(ThemeMode.DARK) { AstNavHost() } }
    }

    private fun ComposeTestRule.awaitText(text: String, substring: Boolean = false) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        runCatching {
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(shots, "ui_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun mainFlowDoesNotCrash() {
        // Proyectos vacío
        rule.awaitText("Todavía no hay proyectos", substring = true)
        rule.onNodeWithContentDescription("Ajustes").assertIsDisplayed()
        shot("1_projects_empty")

        // Crear proyecto
        rule.onNodeWithText("Nuevo proyecto").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Granja Solar X")
        rule.onNodeWithText("Crear").performClick()

        // Entra a la pantalla del proyecto
        rule.awaitText("Capturar aquí")
        rule.awaitText("Sin subcarpetas", substring = true)
        shot("2_project_empty")

        // Subcarpeta tipo "Subestación" con plantilla
        rule.onNodeWithText("Nueva").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Subestación 1")
        rule.awaitText("Crear también", substring = true)
        rule.onNodeWithText("Crear").performClick()
        rule.awaitText("Subestación 1")
        shot("3_project_with_substation")

        // Abrir la subestación: aparecen las 4 subcarpetas estándar
        rule.onNodeWithText("Subestación 1").performClick()
        rule.awaitText("Transformadores")
        rule.onNodeWithText("Tableros").assertExists()
        rule.onNodeWithText("Generadores").assertExists()
        rule.onNodeWithText("Banco de condensadores").assertExists()
        shot("4_substation")

        // Captura con destino en la subestación
        rule.onNodeWithText("Capturar aquí").performClick()
        rule.awaitText("TOMAR FOTO")
        rule.awaitText("Granja Solar X › Subestación 1", substring = true)
        rule.awaitText("±8 m")
        shot("5_capture")

        // Volver a Proyectos y abrir Ajustes
        back(); back(); back()
        rule.awaitText("Granja Solar X")
        shot("6_projects_with_project")
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.awaitText("MARCA DE AGUA")
        rule.onNodeWithContentDescription("Vista previa de la marca de agua").assertExists()
        shot("7_settings")
        rule.onNodeWithText("Guardar copia original sin marca").performScrollTo().assertExists()
    }
}
