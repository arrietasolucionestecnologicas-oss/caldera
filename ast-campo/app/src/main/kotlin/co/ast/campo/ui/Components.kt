@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.ast.campo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.ast.campo.core.FolderNode
import co.ast.campo.core.FolderTemplates
import co.ast.campo.core.FolderTree
import co.ast.campo.core.NameSanitizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val SpanishCo: Locale = Locale.Builder().setLanguage("es").setRegion("CO").build()

/** Mínimo recomendado para acciones principales con guantes o manos ocupadas. */
val BigTarget = 60.dp

fun fotosLabel(n: Int) = if (n == 1) "1 foto" else "%,d fotos".format(SpanishCo, n)

/** "Hoy, 14:05", "Ayer, 16:40", "21 nov, 09:15" o "21 nov 2025". Sin actividad devuelve "Sin fotos". */
fun formatActivity(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (millis <= 0) return "Sin fotos"
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone).let { if (now == System.currentTimeMillis()) it else Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
    val time = DateTimeFormatter.ofPattern("HH:mm").format(dt)
    return when (dt.toLocalDate()) {
        today -> "Hoy, $time"
        today.minusDays(1) -> "Ayer, $time"
        else -> {
            val pattern = if (dt.year == today.year) "d MMM, HH:mm" else "d MMM yyyy"
            DateTimeFormatter.ofPattern(pattern, SpanishCo).format(dt).replace(".", "")
        }
    }
}

@Composable
fun AstTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/** Botón grande de acción principal (naranja). */
@Composable
fun BigButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = BigTarget,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    if (outlined) {
        OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = height), shape = shape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        ) { ButtonContent(text, icon) }
    } else {
        Button(
            onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = height), shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { ButtonContent(text, icon) }
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Chip de datos (GPS, temperatura, ruta). Cian para datos y estados activos. */
@Composable
fun DataChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.heightIn(min = 44.dp)
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.7f)),
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(SpanishCo),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancelar",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(dismissLabel) } },
    )
}

/**
 * Crear o renombrar una carpeta. Valida en el momento (vacío y duplicados sin distinguir mayúsculas) y
 * muestra cómo quedará el nombre ya sanitizado. Con [offerTemplate] ofrece las subcarpetas de "Subestación".
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    siblings: List<String>,
    templateItems: List<String>,
    confirmLabel: String,
    onConfirm: (name: String, withTemplate: Boolean) -> Unit,
    onDismiss: () -> Unit,
    offerTemplate: Boolean = false,
    showSuggestions: Boolean = true,
) {
    var text by remember { mutableStateOf(initial) }
    var template by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    val clean = NameSanitizer.sanitize(text)
    val error = when {
        text.isBlank() -> null
        clean.isEmpty() -> "Escribe un nombre válido"
        NameSanitizer.isDuplicate(clean, siblings) -> "Ya existe \"$clean\" en este nivel"
        else -> null
    }
    val valid = clean.isNotEmpty() && error == null
    val suggestions = remember(siblings) {
        val next = FolderTemplates.nextNumbered(siblings)
        (listOfNotNull(next) + FolderTemplates.QUICK_SUGGESTIONS)
            .filter { !NameSanitizer.isDuplicate(it, siblings) }
            .distinct()
            .take(10)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Nombre") },
                    isError = error != null,
                    supportingText = {
                        when {
                            error != null -> Text(error)
                            clean.isNotEmpty() && clean != text.trim() -> Text("Se guardará como: $clean")
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (showSuggestions && suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestions.forEach { s ->
                            SuggestionChip(onClick = { text = s }, label = { Text(s) }, modifier = Modifier.heightIn(min = 40.dp))
                        }
                    }
                }
                if (offerTemplate && FolderTemplates.isSubstationName(clean) && templateItems.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { template = !template },
                    ) {
                        Checkbox(checked = template, onCheckedChange = { template = it })
                        Text(
                            "Crear también: " + FolderTemplates.cleanTemplate(templateItems).joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clean, template) }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )
}

const val ROOT_ID = 0L

/**
 * Selector de carpeta en árbol (para elegir destino de captura, mover fotos o mover carpetas).
 * [excludedIds] no se pueden elegir ni abrir. Con [allowRoot] aparece "Nivel principal (proyecto)".
 */
@Composable
fun FolderPickerSheet(
    tree: FolderTree,
    title: String,
    confirmLabel: String,
    initialSelectedId: Long?,
    templateItems: List<String>,
    onCreate: (parentId: Long?, name: String, withTemplate: Boolean, onCreated: (Long) -> Unit) -> Unit,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
    excludedIds: Set<Long> = emptySet(),
    allowRoot: Boolean = false,
) {
    var selected by remember { mutableStateOf(initialSelectedId?.takeIf { tree[it] != null } ?: if (allowRoot) ROOT_ID else null) }
    var expanded by remember {
        mutableStateOf(initialSelectedId?.let { id -> tree.pathOf(id).dropLast(1).map { it.id }.toSet() } ?: emptySet())
    }
    var creating by remember { mutableStateOf(false) }

    val rows = remember(tree, expanded, excludedIds) {
        val out = ArrayList<Pair<FolderNode, Int>>()
        fun walk(parent: Long?, depth: Int) {
            tree.childrenOf(parent).filter { it.id !in excludedIds }.forEach { n ->
                out.add(n to depth)
                if (n.id in expanded) walk(n.id, depth + 1)
            }
        }
        walk(null, 0)
        out
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parentForNew: Long? = selected?.takeIf { it != ROOT_ID }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                if (allowRoot) {
                    item(key = "root") {
                        PickerRow(
                            name = "Nivel principal (como proyecto)", depth = 0, hasChildren = false, isOpen = false,
                            isSelected = selected == ROOT_ID, onSelect = { selected = ROOT_ID }, onToggle = {},
                        )
                    }
                }
                items(rows, key = { it.first.id }) { (node, depth) ->
                    val hasChildren = tree.childrenOf(node.id).any { it.id !in excludedIds }
                    PickerRow(
                        name = node.name, depth = depth, hasChildren = hasChildren, isOpen = node.id in expanded,
                        isSelected = selected == node.id,
                        onSelect = { selected = node.id },
                        onToggle = { expanded = if (node.id in expanded) expanded - node.id else expanded + node.id },
                    )
                }
                if (rows.isEmpty() && !allowRoot) {
                    item { Text("Aún no hay proyectos. Crea el primero.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigButton(
                    text = if (parentForNew == null) "Nuevo proyecto" else "Crear subcarpeta aquí",
                    icon = Icons.Filled.CreateNewFolder, onClick = { creating = true }, outlined = true,
                    modifier = Modifier.weight(1f),
                )
                BigButton(
                    text = confirmLabel, icon = null,
                    onClick = { onConfirm(selected.takeIf { it != ROOT_ID }) },
                    enabled = selected != null, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.size(16.dp))
        }
    }
    if (creating) {
        NameDialog(
            title = if (parentForNew == null) "Nuevo proyecto" else "Nueva subcarpeta",
            initial = "",
            siblings = tree.childrenOf(parentForNew).map { it.name },
            templateItems = templateItems,
            confirmLabel = "Crear",
            offerTemplate = parentForNew != null,
            onDismiss = { creating = false },
            onConfirm = { name, withTemplate ->
                creating = false
                onCreate(parentForNew, name, withTemplate) { newId ->
                    selected = newId
                    parentForNew?.let { expanded = expanded + it }
                }
            },
        )
    }
}

@Composable
private fun PickerRow(
    name: String,
    depth: Int,
    hasChildren: Boolean,
    isOpen: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f) else Color.Transparent
    Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 52.dp).padding(start = (depth * 20).dp),
        ) {
            if (hasChildren) {
                IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                    Icon(if (isOpen) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, contentDescription = if (isOpen) "Contraer" else "Expandir")
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Icon(
                if (isOpen) Icons.Filled.FolderOpen else Icons.Filled.Folder, contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
        }
    }
}

@Composable
fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp)

/** Muestra en un Snackbar los mensajes que emite el ViewModel (reemplaza el anterior si llega otro). */
@Composable
fun CollectMessages(messages: kotlinx.coroutines.flow.SharedFlow<String>, host: androidx.compose.material3.SnackbarHostState) {
    androidx.compose.runtime.LaunchedEffect(messages) {
        kotlinx.coroutines.coroutineScope {
            messages.collect { msg ->
                host.currentSnackbarData?.dismiss()
                launch { host.showSnackbar(msg) } // sin bloquear: un aviso nuevo reemplaza al anterior
            }
        }
    }
}

/** Eliminar carpeta: pregunta si también se borran las fotos (conserva el límite de 3 opciones sin desbordar). */
@Composable
fun DeleteFolderDialog(
    name: String,
    photoCount: Int,
    subfolderCount: Int,
    parentName: String?,
    onDeleteAll: () -> Unit,
    onKeepPhotos: () -> Unit,
    onDismiss: () -> Unit,
) {
    val contents = buildString {
        append("Contiene ")
        append(if (subfolderCount == 1) "1 subcarpeta" else "$subfolderCount subcarpetas")
        append(" y ")
        append(fotosLabel(photoCount))
        append(".")
    }
    if (photoCount == 0) {
        ConfirmDialog(
            title = "Eliminar \"$name\"", text = if (subfolderCount == 0) "La carpeta está vacía." else contents,
            confirmLabel = "Eliminar", destructive = true, onConfirm = onDeleteAll, onDismiss = onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar \"$name\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contents)
                Text("¿Qué hacemos con las fotos?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigButton("Eliminar carpeta y fotos", null, onDeleteAll, Modifier.fillMaxWidth(), height = 52.dp)
                BigButton(
                    text = if (parentName != null) "Solo la carpeta: las fotos pasan a \"$parentName\"" else "Solo del listado: las fotos quedan en la Galería",
                    icon = null, onClick = onKeepPhotos, modifier = Modifier.fillMaxWidth(), height = 52.dp, outlined = true,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )
}
