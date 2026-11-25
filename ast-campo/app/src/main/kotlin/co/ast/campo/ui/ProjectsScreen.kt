@file:OptIn(ExperimentalMaterial3Api::class)

package co.ast.campo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.ast.campo.R
import co.ast.campo.core.FolderNode

@Composable
fun ProjectsScreen(
    vm: ProjectsVM,
    onOpenProject: (Long) -> Unit,
    onCapture: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snack)

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderNode?>(null) }
    var deleting by remember { mutableStateOf<ProjectCard?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ast_logo), contentDescription = "A.S.T.", modifier = Modifier.size(56.dp))
                        Text("AST Campo", maxLines = 1)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                BigButton(
                    "Nuevo proyecto", Icons.Filled.CreateNewFolder, { creating = true },
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                )
            }
        },
    ) { padding ->
        val data = ui
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (data != null && (data.totalProjects > 0 || query.isNotEmpty())) {
                OutlinedTextField(
                    value = query, onValueChange = { vm.query.value = it },
                    singleLine = true, placeholder = { Text("Buscar proyecto") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                data == null -> Unit
                data.totalProjects == 0 -> CenteredHint("Todavía no hay proyectos.\nCrea el primero con el botón de abajo.")
                data.projects.isEmpty() -> CenteredHint("Ningún proyecto coincide con \"$query\".")
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.projects, key = { it.node.id }) { card ->
                        ProjectCardView(
                            card,
                            onOpen = { onOpenProject(card.node.id) },
                            onCapture = { onCapture(vm.captureTarget(card.node.id)) },
                            onRename = { renaming = card.node },
                            onDelete = { deleting = card },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Nuevo proyecto", initial = "",
            siblings = ui?.tree?.projects?.map { it.name }.orEmpty(),
            templateItems = settings.substationTemplate, confirmLabel = "Crear", showSuggestions = false,
            onDismiss = { creating = false },
            onConfirm = { name, _ -> creating = false; vm.createFolder(null, name, false) { onOpenProject(it) } },
        )
    }
    renaming?.let { node ->
        NameDialog(
            title = "Renombrar proyecto", initial = node.name,
            siblings = ui?.tree?.projects?.filter { it.id != node.id }?.map { it.name }.orEmpty(),
            templateItems = emptyList(), confirmLabel = "Guardar", showSuggestions = false,
            onDismiss = { renaming = null },
            onConfirm = { name, _ -> renaming = null; vm.renameFolder(node.id, name) },
        )
    }
    deleting?.let { card ->
        val subs = ui?.tree?.subtreeIds(card.node.id)?.size?.minus(1) ?: 0
        DeleteFolderDialog(
            name = card.node.name, photoCount = card.stats.photoCount, subfolderCount = subs, parentName = null,
            onDeleteAll = { deleting = null; vm.deleteFolder(card.node.id, true) },
            onKeepPhotos = { deleting = null; vm.deleteFolder(card.node.id, false) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ProjectCardView(
    card: ProjectCard,
    onOpen: () -> Unit,
    onCapture: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(card.node.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(fotosLabel(card.stats.photoCount), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                Text(
                    "Última actividad: " + formatActivity(card.stats.lastActivity),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; onRename() }, modifier = Modifier.heightIn(min = 52.dp))
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() }, modifier = Modifier.heightIn(min = 52.dp))
                }
            }
            Button(
                onClick = onCapture, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.size(width = 68.dp, height = 60.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Icon(Icons.Filled.CameraAlt, contentDescription = "Capturar en ${card.node.name}", modifier = Modifier.size(30.dp)) }
        }
    }
}
