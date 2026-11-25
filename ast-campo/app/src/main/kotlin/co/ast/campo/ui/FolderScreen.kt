@file:OptIn(ExperimentalMaterial3Api::class)

package co.ast.campo.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import co.ast.campo.core.FolderNode
import co.ast.campo.core.FolderStats
import co.ast.campo.data.PhotoEntity

@Composable
fun FolderScreen(
    vm: FolderVM,
    onBack: () -> Unit,
    onGoBack: (Int) -> Unit,
    onOpenSubfolder: (Long) -> Unit,
    onCapture: (Long) -> Unit,
    onOpenPhoto: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snack)

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderNode?>(null) }
    var moving by remember { mutableStateOf<FolderNode?>(null) }
    var deleting by remember { mutableStateOf<FolderNode?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Si la carpeta se elimina o ya no existe, se vuelve atrás.
    LaunchedEffect(state) { if (state is FolderState.Missing) onBack() }
    val ready = state as? FolderState.Ready
    val ui = ready?.ui

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AstTopBar(
                title = ui?.folder?.name.orEmpty(), onBack = onBack,
                actions = {
                    if (ui != null) {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; renaming = ui.folder }, modifier = Modifier.heightIn(min = 52.dp))
                            DropdownMenuItem(text = { Text("Mover") }, onClick = { menu = false; moving = ui.folder }, modifier = Modifier.heightIn(min = 52.dp))
                            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; deleting = ui.folder }, modifier = Modifier.heightIn(min = 52.dp))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BigButton("Nueva", Icons.Filled.CreateNewFolder, { creating = true }, Modifier.weight(0.8f), outlined = true)
                    BigButton("Capturar aquí", Icons.Filled.CameraAlt, { ui?.let { onCapture(it.folder.id) } }, Modifier.weight(1.2f), enabled = ui != null)
                }
            }
        },
    ) { padding ->
        if (ui == null) {
            Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Breadcrumb(ui.trail, onGoBack) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("Subcarpetas", Modifier.padding(top = 12.dp, bottom = 2.dp))
            }
            if (ui.subfolders.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Sin subcarpetas. Usa \"Nueva\" para crear una aquí.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(ui.subfolders, key = { "f${it.first.id}" }, span = { GridItemSpan(maxLineSpan) }) { (node, stats) ->
                SubfolderRow(
                    node, stats, onClick = { onOpenSubfolder(node.id) },
                    onRename = { renaming = node }, onMove = { moving = node }, onDelete = { deleting = node },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel("Fotos en esta carpeta (${photos.size})", Modifier.padding(top = 16.dp, bottom = 2.dp))
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Todavía no hay fotos aquí. Usa \"Capturar aquí\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(photos, key = { "p${it.id}" }) { p -> PhotoTile(p) { onOpenPhoto(p.id) } }
        }
    }

    if (creating && ui != null) {
        NameDialog(
            title = "Nueva subcarpeta en \"${ui.folder.name}\"", initial = "",
            siblings = ui.subfolders.map { it.first.name }, templateItems = settings.substationTemplate,
            confirmLabel = "Crear", offerTemplate = true,
            onDismiss = { creating = false },
            onConfirm = { name, template -> creating = false; vm.createFolder(ui.folder.id, name, template) },
        )
    }
    renaming?.let { node ->
        val siblings = ui?.tree?.childrenOf(node.parentId)?.filter { it.id != node.id }?.map { it.name }.orEmpty()
        NameDialog(
            title = "Renombrar carpeta", initial = node.name, siblings = siblings, templateItems = emptyList(),
            confirmLabel = "Guardar", showSuggestions = false,
            onDismiss = { renaming = null },
            onConfirm = { name, _ -> renaming = null; vm.renameFolder(node.id, name) },
        )
    }
    moving?.let { node ->
        ui?.tree?.let { tree ->
            FolderPickerSheet(
                tree = tree, title = "Mover \"${node.name}\" a…", confirmLabel = "Mover aquí",
                initialSelectedId = node.parentId, templateItems = settings.substationTemplate,
                excludedIds = tree.subtreeIds(node.id).toSet(), allowRoot = true,
                onCreate = { parent, name, template, done -> vm.createFolder(parent, name, template, done) },
                onConfirm = { dest ->
                    moving = null
                    vm.moveFolder(node.id, dest)
                    // Si se movió la carpeta abierta, la pila de pantallas ya no coincide con su ruta: se vuelve atrás.
                    if (node.id == ui.folder.id) onBack()
                },
                onDismiss = { moving = null },
            )
        }
    }
    deleting?.let { node ->
        val tree = ui?.tree
        val stats = if (node.id == ui?.folder?.id) ui.total else ui?.subfolders?.firstOrNull { it.first.id == node.id }?.second ?: FolderStats()
        DeleteFolderDialog(
            name = node.name, photoCount = stats.photoCount,
            subfolderCount = (tree?.subtreeIds(node.id)?.size ?: 1) - 1,
            parentName = node.parentId?.let { tree?.get(it)?.name },
            onDeleteAll = { deleting = null; vm.deleteFolder(node.id, true) },
            onKeepPhotos = { deleting = null; vm.deleteFolder(node.id, false) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Breadcrumb(trail: List<FolderNode>, onGoBack: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        trail.forEachIndexed { i, node ->
            val last = i == trail.lastIndex
            item(key = node.id) {
                val color = if (last) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    shape = CircleShape,
                    color = if (last) color.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
                    border = BorderStroke(1.dp, color.copy(alpha = if (last) 0.7f else 0.4f)),
                    modifier = Modifier.heightIn(min = 44.dp).clickable(enabled = !last) { onGoBack(trail.lastIndex - i) },
                ) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(node.name, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (!last) {
                item(key = "sep${node.id}") {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SubfolderRow(
    node: FolderNode, stats: FolderStats,
    onClick: () -> Unit, onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(14.dp))
            Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(stats.photoCount.toString(), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; onRename() }, modifier = Modifier.heightIn(min = 52.dp))
                    DropdownMenuItem(text = { Text("Mover") }, onClick = { menu = false; onMove() }, modifier = Modifier.heightIn(min = 52.dp))
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() }, modifier = Modifier.heightIn(min = 52.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: PhotoEntity, onClick: () -> Unit) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(Uri.parse(photo.mediaUri)).size(360).build(),
        contentDescription = photo.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    )
}
