@file:OptIn(ExperimentalMaterial3Api::class)

package co.ast.campo.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import co.ast.campo.AstApp
import co.ast.campo.core.WatermarkFormatter
import co.ast.campo.weather.TempSource
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(vm: CaptureVM, onBack: () -> Unit, onOpenPhoto: (folderId: Long, photoId: Long) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as AstApp
    val tree by vm.tree.collectAsStateWithLifecycle()
    val segments by vm.destSegments.collectAsStateWithLifecycle()
    val dest by vm.dest.collectAsStateWithLifecycle()
    val sessionCount by vm.sessionCount.collectAsStateWithLifecycle()
    val lastPhoto by vm.lastPhoto.collectAsStateWithLifecycle()
    val processing by vm.processing.collectAsStateWithLifecycle()
    val temp by vm.temp.collectAsStateWithLifecycle()
    val manual by vm.manualTemp.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fix by app.tracker.fix.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snack)

    var hasPermission by remember { mutableStateOf(app.tracker.hasPermission()) }
    var denials by rememberSaveable { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = app.tracker.hasPermission()
        if (!hasPermission) denials += 1
    }
    fun requestLocation() {
        if (denials >= 2) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // La cámara de Samsung se abre por intent (TakePicture); al volver, el resultado va al CaptureManager.
    var cameraBusy by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        cameraBusy = false
        app.captureManager.onCameraResult(ok)
    }

    LaunchedEffect(Unit) { if (!app.tracker.hasPermission()) requestLocation() }

    // Ubicación activa solo mientras la pantalla está visible; temperatura al día en el mismo lapso.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // El permiso pudo cambiar fuera de la app (Ajustes, "solo esta vez" vencido).
                    hasPermission = app.tracker.hasPermission()
                    app.tracker.start()
                }
                Lifecycle.Event.ON_STOP -> app.tracker.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            app.tracker.stop()
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.temperatureLoop() }
    }
    val nowElapsed by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(2_000)
        }
    }

    var picking by remember { mutableStateOf(false) }
    var editingTemp by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    fun takePhoto() {
        if (cameraBusy) return // un doble toque no debe abrir dos cámaras
        val uri = vm.prepareCapture()
        if (uri == null) {
            picking = true
            return
        }
        try {
            cameraBusy = true
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            cameraBusy = false
            vm.toast("No se encontró una app de cámara en el teléfono")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AstTopBar("Captura", onBack = onBack) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SaveStatus(processing, sessionCount)
                    Spacer(Modifier.height(8.dp))
                    BigButton("TOMAR FOTO", Icons.Filled.CameraAlt, ::takePhoto, Modifier.fillMaxWidth(), height = 96.dp)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Destino")
            DestinationChip(segments, onClick = { picking = true })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val gps = gpsChipState(hasPermission, app.tracker.isGpsEnabled(), fix?.accuracyM, fix?.ageMs(nowElapsed))
                DataChip(
                    gps.text, gps.icon, tint = gps.kind.tint(),
                    onClick = when (gps.action) {
                        GpsAction.REQUEST_PERMISSION -> ::requestLocation
                        GpsAction.OPEN_LOCATION_SETTINGS -> ({ ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
                        null -> null
                    },
                )
                val tempText = temp.celsius?.let { WatermarkFormatter.temperature(it) } ?: "-- °C"
                DataChip(tempText, Icons.Filled.Thermostat, onClick = { editingTemp = true })
            }
            Text(
                when (temp.source) {
                    TempSource.MANUAL -> "Temperatura escrita a mano. Toca el chip para volver a automática."
                    TempSource.CACHE -> "Sin internet: se usa la última temperatura guardada (zona cercana, menos de 60 min)."
                    TempSource.NETWORK -> "Temperatura de referencia de la zona (internet), no medida en el sitio."
                    TempSource.NONE -> "Sin temperatura automática. Toca el chip para escribirla a mano."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CameraAlt, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Lista para la siguiente foto", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Se abre la cámara de Samsung. La marca se aplica al volver.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (processing > 0) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.secondary)
                        Text("Guardando $processing foto(s)…", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val p = lastPhoto
                    if (p != null) {
                        MediaThumbnail(
                            uri = p.mediaUri, sizePx = 320, description = "Última foto: toca para revisarla",
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenPhoto(p.folderId, p.id) },
                        )
                    } else {
                        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (p != null) "Toca la foto para revisarla" else "Aún no hay fotos en esta sesión", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Sesión: " + fotosLabel(sessionCount), color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (p != null) {
                        OutlinedButton(
                            onClick = { confirmUndo = true }, shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.height(52.dp),
                        ) {
                            Icon(Icons.Filled.Undo, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(6.dp))
                            Text("Deshacer", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (picking) {
        val t = tree
        if (t != null) {
            FolderPickerSheet(
                tree = t, title = "Carpeta de destino", confirmLabel = "Usar este destino",
                initialSelectedId = dest, templateItems = settings.substationTemplate,
                onCreate = { parent, name, template, done -> vm.createFolder(parent, name, template, done) },
                onConfirm = { id -> picking = false; id?.let(vm::setDestination) },
                onDismiss = { picking = false },
            )
        }
    }
    if (editingTemp) {
        TemperatureDialog(
            current = manual ?: temp.celsius, isManual = manual != null,
            onUse = { editingTemp = false; vm.setManualTemperature(it) },
            onAuto = { editingTemp = false; vm.setManualTemperature(null) },
            onDismiss = { editingTemp = false },
        )
    }
    if (confirmUndo) {
        ConfirmDialog(
            title = "Eliminar la última foto", text = "Se borrará de la Galería y de la app. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar", destructive = true,
            onConfirm = { confirmUndo = false; vm.undoLast() }, onDismiss = { confirmUndo = false },
        )
    }
}

/** Estado de guardado siempre visible junto al botón: se sabe si hay fotos guardándose y que se puede seguir disparando. */
@Composable
private fun SaveStatus(processing: Int, sessionCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)) {
        if (processing > 0) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text(
                if (processing == 1) "Guardando la foto… puedes tomar otra, se guarda sola"
                else "Guardando $processing fotos… puedes seguir tomando, se guardan solas",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary,
            )
        } else if (sessionCount > 0) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Todo guardado · ${fotosLabel(sessionCount)} en esta sesión",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary,
            )
        } else {
            Text("Lista para tomar la primera foto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DestinationChip(segments: List<String>?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                segments?.joinToString(WatermarkFormatter.PATH_SEPARATOR) ?: "Elegir carpeta de destino",
                style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = "Cambiar destino", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class GpsAction { REQUEST_PERMISSION, OPEN_LOCATION_SETTINGS }

private enum class GpsKind { OK, WARN, SEARCHING }

private data class GpsChip(
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val kind: GpsKind,
    val action: GpsAction? = null,
)

@Composable
private fun GpsKind.tint() = when (this) {
    GpsKind.OK -> MaterialTheme.colorScheme.secondary
    GpsKind.WARN -> if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        androidx.compose.ui.graphics.Color(0xFF8A5A00)
    } else {
        AstColors.Warn
    }
    GpsKind.SEARCHING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun gpsChipState(hasPermission: Boolean, gpsOn: Boolean, accuracyM: Float?, ageMs: Long?): GpsChip = when {
    !hasPermission -> GpsChip("Permitir ubicación", Icons.Filled.LocationOff, GpsKind.WARN, GpsAction.REQUEST_PERMISSION)
    !gpsOn -> GpsChip("GPS apagado", Icons.Filled.LocationOff, GpsKind.WARN, GpsAction.OPEN_LOCATION_SETTINGS)
    accuracyM == null || ageMs == null -> GpsChip("Buscando GPS…", Icons.Filled.GpsNotFixed, GpsKind.SEARCHING)
    ageMs > 30_000 -> GpsChip("Sin señal reciente", Icons.Filled.GpsNotFixed, GpsKind.WARN)
    accuracyM > CaptureVM.WEAK_ACCURACY_M -> GpsChip("±${accuracyM.toInt()} m (baja)", Icons.Filled.GpsFixed, GpsKind.WARN)
    else -> GpsChip("±${accuracyM.toInt()} m", Icons.Filled.GpsFixed, GpsKind.OK)
}

@Composable
private fun TemperatureDialog(current: Double?, isManual: Boolean, onUse: (Double) -> Unit, onAuto: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.let { "%.0f".format(java.util.Locale.US, it) }.orEmpty()) }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    val valid = value != null && value in -40.0..70.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temperatura a mano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Escribe la temperatura medida en el sitio. Reemplaza a la automática en las próximas fotos (hasta 3 horas).")
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, label = { Text("°C") },
                    isError = text.isNotBlank() && !valid,
                    supportingText = { if (text.isNotBlank() && !valid) Text("Entre -40 y 70 °C") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onUse(value!!) }, enabled = valid, modifier = Modifier.height(48.dp)) { Text("Usar") } },
        dismissButton = {
            Row {
                if (isManual) TextButton(onClick = onAuto, modifier = Modifier.height(48.dp)) { Text("Automática") }
                TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("Cancelar") }
            }
        },
    )
}
