@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.ast.campo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.ast.campo.BuildConfig
import co.ast.campo.data.AppSettings
import co.ast.campo.data.Corner
import co.ast.campo.data.ThemeMode
import co.ast.campo.data.WatermarkSettings
import co.ast.campo.data.WatermarkSize
import co.ast.campo.data.WeatherMode
import co.ast.campo.media.SamplePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: SettingsVM, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val wm = s.watermark
    fun setWm(change: (WatermarkSettings) -> WatermarkSettings) = vm.update { it.copy(watermark = change(it.watermark)) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { AstTopBar("Ajustes", onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ------------------------------------------------------------ Marca de agua
            SectionLabel("Marca de agua")
            WatermarkPreview(wm)
            Text("Vista previa en vivo sobre una foto de ejemplo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchRow("Ruta de destino", "Proyecto › subcarpetas", wm.showRoute) { v -> setWm { it.copy(showRoute = v) } }
            SwitchRow("Fecha y hora", "dd/MM/yyyy HH:mm", wm.showDateTime) { v -> setWm { it.copy(showDateTime = v) } }
            SwitchRow("Coordenadas", "6 decimales", wm.showCoords) { v -> setWm { it.copy(showCoords = v) } }
            SwitchRow("Temperatura", "De referencia de la zona", wm.showTemp) { v -> setWm { it.copy(showTemp = v) } }
            SwitchRow(
                "Lugar (nombre del sitio)", "Dirección o sitio aproximado según el servicio de ubicación de Google. Necesita internet y le envía las coordenadas a Google.",
                wm.showPlace,
            ) { v -> setWm { it.copy(showPlace = v) } }

            Text("Esquina", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Corner.BOTTOM_LEFT to "Inferior izquierda", Corner.BOTTOM_RIGHT to "Inferior derecha",
                    Corner.TOP_LEFT to "Superior izquierda", Corner.TOP_RIGHT to "Superior derecha",
                ).forEach { (c, label) ->
                    FilterChip(
                        selected = wm.corner == c, onClick = { setWm { it.copy(corner = c) } }, label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            Text("Tamaño", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(WatermarkSize.SMALL to "Pequeño", WatermarkSize.MEDIUM to "Mediano", WatermarkSize.LARGE to "Grande").forEachIndexed { i, (size, label) ->
                    SegmentedButton(
                        selected = wm.size == size, onClick = { setWm { it.copy(size = size) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 3), modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(label) }
                }
            }

            SwitchRow(
                "Fondo azul marino", "Panel translúcido con barra naranja. Apagado: solo letras con una sombra suave.",
                wm.background,
            ) { v -> setWm { it.copy(background = v) } }
            if (wm.background) {
                Text("Opacidad del fondo: ${wm.opacityPct} %", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = wm.opacityPct.toFloat(), onValueChange = { v -> setWm { it.copy(opacityPct = v.toInt().coerceIn(30, 90)) } },
                    valueRange = 30f..90f, modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            OutlinedTextField(
                value = wm.company, onValueChange = { v -> setWm { it.copy(company = v.take(24)) } },
                singleLine = true, label = { Text("Texto de empresa (opcional)") }, placeholder = { Text("A.S.T.") },
                modifier = Modifier.fillMaxWidth(),
            )

            // ------------------------------------------------------------ Plantilla
            SectionLabel("Plantilla de Subestación", Modifier.padding(top = 20.dp))
            Text(
                "Al crear una subcarpeta cuyo nombre empiece por \"Subestación\", se ofrece crear estas subcarpetas de una vez.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TemplateEditor(s.substationTemplate) { list -> vm.update { it.copy(substationTemplate = list) } }

            // ------------------------------------------------------------ Temperatura
            SectionLabel("Temperatura", Modifier.padding(top = 20.dp))
            WeatherSection(s, vm::update)

            // ------------------------------------------------------------ Fotos
            SectionLabel("Fotos", Modifier.padding(top = 20.dp))
            SwitchRow(
                "Guardar copia original sin marca", "En el almacenamiento privado de la app, organizada igual y sin aparecer en la Galería. " +
                    "Se ve desde el visor (\"Original\"). Se borra si desinstalas la app.",
                s.saveOriginal,
            ) { v -> vm.update { it.copy(saveOriginal = v) } }

            // ------------------------------------------------------------ Apariencia
            SectionLabel("Apariencia", Modifier.padding(top = 20.dp))
            RadioRow("Oscuro (predeterminado)", "Azul marino con acento naranja", s.theme == ThemeMode.DARK) { vm.update { it.copy(theme = ThemeMode.DARK) } }
            RadioRow("Claro de alto contraste", "Para ver mejor al sol", s.theme == ThemeMode.LIGHT_HC) { vm.update { it.copy(theme = ThemeMode.LIGHT_HC) } }

            // ------------------------------------------------------------ Acerca de
            SectionLabel("Acerca de", Modifier.padding(top = 20.dp))
            Text("AST Campo ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sin anuncios, sin cuentas, sin analítica. Las fotos nunca salen del teléfono; solo se envían a internet las " +
                    "coordenadas: redondeadas a ~1 km a Open-Meteo para la temperatura y, si activas \"Lugar\", exactas al " +
                    "servicio de ubicación de Google para el nombre del sitio.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun WatermarkPreview(wm: WatermarkSettings) {
    val bitmap by produceState<ImageBitmap?>(null, wm) {
        value = withContext(Dispatchers.Default) { SamplePhoto.render(wm).asImageBitmap() }
    }
    val b = bitmap
    val shape = RoundedCornerShape(14.dp)
    if (b != null) {
        Image(
            bitmap = b, contentDescription = "Vista previa de la marca de agua",
            modifier = Modifier.fillMaxWidth().aspectRatio(SamplePhoto.WIDTH / SamplePhoto.HEIGHT.toFloat()).clip(shape),
        )
    } else {
        Spacer(Modifier.fillMaxWidth().aspectRatio(SamplePhoto.WIDTH / SamplePhoto.HEIGHT.toFloat()).clip(shape))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onChecked(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TemplateEditor(items: List<String>, onChange: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item, onValueChange = { v -> onChange(items.toMutableList().also { it[i] = v }) },
                    singleLine = true, modifier = Modifier.weight(1f), label = { Text("Subcarpeta ${i + 1}") },
                )
                IconButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar")
                }
            }
        }
        BigButton("Agregar subcarpeta", Icons.Filled.AddCircleOutline, { onChange(items + "") }, Modifier.fillMaxWidth(), height = 52.dp, outlined = true)
    }
}

@Composable
private fun WeatherSection(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    RadioRow(
        "Open-Meteo gratuito", "Solo para uso NO comercial (hasta 10.000 consultas al día).",
        s.weatherMode == WeatherMode.FREE,
    ) { update { it.copy(weatherMode = WeatherMode.FREE) } }
    RadioRow(
        "Open-Meteo plan comercial", "Con clave API (customer-api.open-meteo.com).",
        s.weatherMode == WeatherMode.COMMERCIAL,
    ) { update { it.copy(weatherMode = WeatherMode.COMMERCIAL) } }
    if (s.weatherMode == WeatherMode.COMMERCIAL) {
        OutlinedTextField(
            value = s.weatherApiKey, onValueChange = { v -> update { it.copy(weatherApiKey = v.trim()) } },
            singleLine = true, label = { Text("Clave API") },
            isError = s.weatherApiKey.isBlank(),
            supportingText = { if (s.weatherApiKey.isBlank()) Text("Sin clave no se consulta la temperatura automática.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    RadioRow(
        "Solo manual", "No usa internet: escribes la temperatura en la pantalla de captura.",
        s.weatherMode == WeatherMode.OFF,
    ) { update { it.copy(weatherMode = WeatherMode.OFF) } }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Es la temperatura de REFERENCIA de la zona", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text(
                "Viene de un modelo meteorológico (celdas de 1 a 11 km), no está medida en el sitio. El teléfono no tiene sensor de " +
                    "temperatura ambiente. Si el dato importa para el informe (por ejemplo la corrección de ensayos), mídela y " +
                    "escríbela a mano en la pantalla de captura.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Datos meteorológicos: Open-Meteo.com (CC BY 4.0).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
