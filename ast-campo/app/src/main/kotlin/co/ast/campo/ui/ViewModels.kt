package co.ast.campo.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ast.campo.AstApp
import co.ast.campo.core.FolderNode
import co.ast.campo.core.FolderStats
import co.ast.campo.core.FolderTree
import co.ast.campo.core.NameSanitizer
import co.ast.campo.data.MoveReport
import co.ast.campo.data.PhotoEntity
import co.ast.campo.data.UserError
import co.ast.campo.media.CaptureEvent
import co.ast.campo.weather.TempResult
import co.ast.campo.weather.TempSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext

/** Base: mensajes para el Snackbar, ejecución segura de operaciones y acciones comunes sobre carpetas. */
abstract class BaseVM(protected val app: AstApp) : ViewModel() {
    protected val repo get() = app.repository
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    val settings get() = app.settings.settings

    val tree: StateFlow<FolderTree?> = repo.tree.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    /** [durable]: la operación debe terminar aunque se cierre la pantalla (mover o borrar muchas fotos). */
    protected fun run(durable: Boolean = false, block: suspend () -> String?) {
        (if (durable) app.appScope else viewModelScope).launch {
            try {
                block()?.let(::toast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserError) {
                toast(e.message ?: "No se pudo completar la acción")
            } catch (e: Exception) {
                toast("Algo falló: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun report(r: MoveReport): String? =
        if (r.failed > 0) "${r.failed} foto(s) no se pudieron actualizar en la Galería" else null

    fun createFolder(parentId: Long?, name: String, withTemplate: Boolean, onCreated: (Long) -> Unit = {}) = run {
        onCreated(repo.createFolder(parentId, name, withTemplate))
        null
    }

    fun renameFolder(id: Long, name: String) = run(durable = true) { report(repo.renameFolder(id, name)) }
    fun moveFolder(id: Long, newParentId: Long?) = run(durable = true) { report(repo.moveFolder(id, newParentId)) }
    fun deleteFolder(id: Long, deletePhotos: Boolean) = run(durable = true) { report(repo.deleteFolder(id, deletePhotos)) }
}

// ------------------------------------------------------------------------------------ Proyectos

data class ProjectCard(val node: FolderNode, val stats: FolderStats)

data class ProjectsUi(val projects: List<ProjectCard>, val totalProjects: Int, val tree: FolderTree)

class ProjectsVM(app: AstApp) : BaseVM(app) {
    val query = MutableStateFlow("")

    val ui: StateFlow<ProjectsUi?> = combine(repo.statsFlow(repo.tree), query) { (tree, stats), q ->
        val key = NameSanitizer.searchKey(q.trim())
        val all = tree.projects
        ProjectsUi(
            projects = all.filter { key.isEmpty() || NameSanitizer.searchKey(it.name).contains(key) }
                .map { ProjectCard(it, stats[it.id] ?: FolderStats()) }
                .sortedByDescending { it.stats.lastActivity.takeIf { t -> t > 0 } ?: it.node.createdAt },
            totalProjects = all.size,
            tree = tree,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Destino de captura de un proyecto: el último usado dentro de él, o el proyecto mismo. */
    fun captureTarget(projectId: Long): Long {
        val t = tree.value
        val last = app.settings.lastDestination(projectId)
        return if (t != null && last != null && t.projectOf(last)?.id == projectId) last else projectId
    }
}

// ------------------------------------------------------------------------------------ Carpeta

data class FolderUi(
    val folder: FolderNode,
    val trail: List<FolderNode>,
    val subfolders: List<Pair<FolderNode, FolderStats>>,
    val total: FolderStats,
    val subtreeCount: Int,
    val tree: FolderTree,
)

sealed interface FolderState {
    data object Loading : FolderState
    data object Missing : FolderState
    data class Ready(val ui: FolderUi) : FolderState
}

class FolderVM(app: AstApp, val folderId: Long) : BaseVM(app) {
    val state: StateFlow<FolderState> = repo.statsFlow(repo.tree).map { (tree, stats) ->
        val node = tree[folderId]
        if (node == null) FolderState.Missing else FolderState.Ready(
            FolderUi(
                folder = node,
                trail = tree.pathOf(folderId),
                subfolders = tree.childrenOf(folderId).map { c -> c to (stats[c.id] ?: FolderStats()) },
                total = stats[folderId] ?: FolderStats(),
                subtreeCount = tree.subtreeIds(folderId).size - 1,
                tree = tree,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FolderState.Loading)

    val photos: StateFlow<List<PhotoEntity>> =
        repo.photosOf(folderId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        run { repo.pruneMissing(folderId); null }
    }
}

// ------------------------------------------------------------------------------------ Captura

class CaptureVM(app: AstApp, private val handle: SavedStateHandle, initialFolder: Long) : BaseVM(app) {
    val dest: StateFlow<Long> = handle.getStateFlow("dest", initialFolder)

    /** Ruta de destino, o null si la carpeta ya no existe. */
    val destSegments: StateFlow<List<String>?> = combine(tree, dest) { t, d ->
        t?.segments(d)?.takeIf { it.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()
    private val _lastPhoto = MutableStateFlow<PhotoEntity?>(null)
    val lastPhoto: StateFlow<PhotoEntity?> = _lastPhoto.asStateFlow()
    val processing: StateFlow<Int> = app.captureManager.processing

    private val _manualTemp = MutableStateFlow(app.settings.manualTemperature())
    val manualTemp: StateFlow<Double?> = _manualTemp.asStateFlow()
    private val _temp = MutableStateFlow(TempResult(null, TempSource.NONE))
    val temp: StateFlow<TempResult> = _temp.asStateFlow()

    init {
        viewModelScope.launch {
            // El árbol puede no haber cargado todavía al crear el ViewModel.
            tree.filterNotNull().first().projectOf(dest.value)?.let { app.settings.setLastDestination(it.id, dest.value) }
        }
        viewModelScope.launch {
            app.captureManager.events.collect { e ->
                when (e) {
                    is CaptureEvent.Saved -> {
                        _sessionCount.value += 1
                        _lastPhoto.value = e.photo
                        savedWarning(e)?.let(::toast)
                    }
                    is CaptureEvent.Failed -> toast("No se pudo guardar la foto: ${e.message}")
                }
            }
        }
    }

    private fun savedWarning(e: CaptureEvent.Saved): String? = when {
        e.watermarkFailed -> "Foto guardada SIN marca de agua (no se pudo dibujar)"
        e.exifFailed -> "Foto guardada con marca, pero sin datos EXIF (GPS y fecha)"
        !e.hasLocation -> "Foto guardada sin coordenadas (sin ubicación)"
        e.accuracyM != null && e.accuracyM > WEAK_ACCURACY_M -> "Foto guardada. Precisión de ubicación baja (±${e.accuracyM.toInt()} m)"
        e.downscaled -> "Foto guardada a menor resolución (memoria insuficiente)"
        else -> null
    }

    fun setDestination(folderId: Long) {
        handle["dest"] = folderId
        rememberDestination(folderId)
    }

    private fun rememberDestination(folderId: Long) {
        tree.value?.projectOf(folderId)?.let { app.settings.setLastDestination(it.id, folderId) }
    }

    fun setManualTemperature(celsius: Double?) {
        app.settings.setManualTemperature(celsius)
        _manualTemp.value = celsius
        viewModelScope.launch { refreshTemperature() }
    }

    private suspend fun refreshTemperature() {
        _manualTemp.value = app.settings.manualTemperature() // la temperatura a mano caduca a las 3 h
        val f = app.tracker.fix.value
        _temp.value = app.temperature.resolve(f?.lat, f?.lon, _manualTemp.value)
    }

    /** Mantiene al día la temperatura del chip mientras la pantalla está visible (se cancela al salir). */
    suspend fun temperatureLoop() {
        while (currentCoroutineContext().isActive) {
            refreshTemperature()
            delay(if (app.tracker.fix.value == null) 3_000L else 20_000L)
        }
    }

    /** Prepara la URI donde la cámara dejará la foto. Null si no hay destino válido. */
    fun prepareCapture(): Uri? {
        if (destSegments.value == null) return null
        _manualTemp.value = app.settings.manualTemperature()
        return app.captureManager.prepare(dest.value, _manualTemp.value)
    }

    fun undoLast() {
        val p = _lastPhoto.value ?: return
        run {
            repo.deletePhoto(p.id)
            _lastPhoto.value = null
            _sessionCount.value = (_sessionCount.value - 1).coerceAtLeast(0)
            "Última foto eliminada"
        }
    }

    companion object {
        const val WEAK_ACCURACY_M = 30f
    }
}

// ------------------------------------------------------------------------------------ Visor

class ViewerVM(app: AstApp, val folderId: Long) : BaseVM(app) {
    val photos: StateFlow<List<PhotoEntity>?> =
        repo.photosOf(folderId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun delete(photoId: Long, onDone: () -> Unit = {}) = run {
        repo.deletePhoto(photoId)
        onDone()
        "Foto eliminada"
    }

    fun move(photoId: Long, destFolderId: Long) = run(durable = true) {
        repo.movePhoto(photoId, destFolderId)
        "Foto movida"
    }
}

// ------------------------------------------------------------------------------------ Ajustes

class SettingsVM(app: AstApp) : BaseVM(app) {
    fun update(change: (co.ast.campo.data.AppSettings) -> co.ast.campo.data.AppSettings) = app.settings.update(change)
}
