@file:OptIn(ExperimentalMaterial3Api::class)

package co.ast.campo.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import co.ast.campo.core.FolderTree
import co.ast.campo.core.WatermarkFormatter
import co.ast.campo.data.PhotoEntity
import co.ast.campo.weather.TempSource
import java.io.File
import java.time.ZoneId

@Composable
fun ViewerScreen(vm: ViewerVM, startPhotoId: Long, onBack: () -> Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val list = photos
    when {
        list == null -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> LaunchedEffect(Unit) { onBack() }
        else -> ViewerContent(vm, list, startPhotoId, onBack)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerContent(vm: ViewerVM, list: List<PhotoEntity>, startPhotoId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tree by vm.tree.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snack)

    val initial = remember { list.indexOfFirst { it.id == startPhotoId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = initial) { list.size }
    val photo = list[pager.currentPage.coerceIn(0, list.lastIndex)]

    var originalFor by remember { mutableStateOf<Long?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Si la foto con zoom desaparece (borrada o movida) no debe quedar bloqueado el deslizamiento.
    LaunchedEffect(photo.id) { zoomed = false }
    val hasOriginal = photo.originalPath?.let { File(it).exists() } == true

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("${pager.currentPage + 1} de ${list.size}") },
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = { details = true }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Info, "Detalles") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0A0F1A)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionButton("Mover", Icons.AutoMirrored.Filled.DriveFileMove) { moving = true }
                ActionButton("Compartir", Icons.Filled.Share) { share(ctx, photo) }
                ActionButton(
                    if (originalFor == photo.id) "Con marca" else "Original",
                    if (originalFor == photo.id) Icons.Filled.Photo else Icons.Filled.PhotoLibrary,
                    enabled = hasOriginal,
                ) { originalFor = if (originalFor == photo.id) null else photo.id }
                ActionButton("Eliminar", Icons.Filled.DeleteOutline, tint = AstColors.Error) { deleting = true }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { list[it].id }, modifier = Modifier.fillMaxSize()) { page ->
                val p = list[page]
                val showOriginal = originalFor == p.id && p.originalPath != null
                ZoomableImage(
                    model = if (showOriginal) File(p.originalPath) else Uri.parse(p.mediaUri),
                    description = p.displayName,
                    onZoomChange = { zoomed = it },
                )
            }
            if (originalFor == photo.id) {
                Text(
                    "ORIGINAL SIN MARCA", color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(Color(0xCC0A1F44), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (details) {
        ModalBottomSheet(onDismissRequest = { details = false }) {
            DetailsContent(photo, tree)
        }
    }
    if (moving) {
        tree?.let { t ->
            FolderPickerSheet(
                tree = t, title = "Mover foto a…", confirmLabel = "Mover aquí",
                initialSelectedId = photo.folderId, templateItems = settings.substationTemplate,
                onCreate = { parent, name, template, done -> vm.createFolder(parent, name, template, done) },
                onConfirm = { id -> moving = false; id?.let { vm.move(photo.id, it) } },
                onDismiss = { moving = false },
            )
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "Eliminar foto", text = "Se borrará de la Galería y de la app, junto con su copia original. No se puede deshacer.",
            confirmLabel = "Eliminar", destructive = true,
            onConfirm = { deleting = false; vm.delete(photo.id) }, onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    val color = if (enabled) tint else Color(0xFF5B6678)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.heightIn(min = 64.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

private fun share(ctx: android.content.Context, photo: PhotoEntity) {
    val uri = Uri.parse(photo.mediaUri)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(photo.displayName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Compartir foto"))
}

@Composable
private fun DetailsContent(p: PhotoEntity, tree: FolderTree?) {
    val route = tree?.segments(p.folderId)?.joinToString(WatermarkFormatter.PATH_SEPARATOR).orEmpty()
    val tempSource = when (p.temperatureSource) {
        TempSource.MANUAL.name -> "escrita a mano"
        TempSource.NETWORK.name -> "referencia de la zona (internet)"
        TempSource.CACHE.name -> "última guardada (sin internet)"
        else -> "sin dato"
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(route, style = MaterialTheme.typography.titleMedium)
        DetailRow("Fecha", WatermarkFormatter.dateTime(p.takenAt, ZoneId.systemDefault()))
        DetailRow(
            "Ubicación",
            if (p.latitude != null && p.longitude != null) {
                WatermarkFormatter.coordinates(p.latitude, p.longitude) + (p.accuracyM?.let { "  (±${it.toInt()} m)" } ?: "")
            } else "Sin coordenadas",
            highlight = p.latitude != null,
        )
        DetailRow("Temperatura", (p.temperatureC?.let { WatermarkFormatter.temperature(it) } ?: "--") + "  ($tempSource)")
        DetailRow("Resolución", "${p.width} × ${p.height}  (${"%.1f".format(SpanishCo, p.width * p.height / 1_000_000.0)} MP)")
        DetailRow("Tamaño", "%.1f MB".format(SpanishCo, p.sizeBytes / 1_048_576.0))
        DetailRow("Copia original", if (p.originalPath != null) "Guardada (sin marca)" else "No guardada")
        DetailRow("Archivo", p.displayName)
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(width = 112.dp, height = 24.dp))
        Text(value, color = if (highlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** Imagen con zoom por pellizco y doble toque. Con zoom 1x deja pasar el gesto al pager para cambiar de foto. */
@Composable
private fun ZoomableImage(model: Any?, description: String?, onZoomChange: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    onZoomChange(scale > 1f)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            val ns = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            scale = ns
                            offset = if (ns > 1f) clamp(offset + event.calculatePan(), ns) else Offset.Zero
                            onZoomChange(ns > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(model).size(4096).build(),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}
