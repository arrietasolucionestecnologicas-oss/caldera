package co.ast.campo

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.ast.campo.ui.CaptureScreen
import co.ast.campo.ui.CaptureVM
import co.ast.campo.ui.FolderScreen
import co.ast.campo.ui.FolderVM
import co.ast.campo.ui.ProjectsScreen
import co.ast.campo.ui.ProjectsVM
import co.ast.campo.ui.SettingsScreen
import co.ast.campo.ui.SettingsVM
import co.ast.campo.ui.ViewerScreen
import co.ast.campo.ui.ViewerVM

private object Routes {
    const val PROJECTS = "projects"
    const val FOLDER = "folder/{id}"
    const val CAPTURE = "capture/{id}"
    const val VIEWER = "viewer/{folderId}/{photoId}"
    const val SETTINGS = "settings"
}

private val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

@Composable
fun AstNavHost() {
    val app = LocalContext.current.applicationContext as AstApp
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.PROJECTS) {
        composable(Routes.PROJECTS) {
            val vm = viewModel { ProjectsVM(app) }
            ProjectsScreen(
                vm,
                onOpenProject = { nav.navigate("folder/$it") },
                onCapture = { nav.navigate("capture/$it") },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.FOLDER, arguments = listOf(longArg("id"))) { entry ->
            val id = entry.arguments!!.getLong("id")
            val vm = viewModel(key = "folder$id") { FolderVM(app, id) }
            FolderScreen(
                vm,
                onBack = { nav.popBackStack() },
                onGoBack = { steps -> repeat(steps) { nav.popBackStack() } },
                onOpenSubfolder = { nav.navigate("folder/$it") },
                onCapture = { nav.navigate("capture/$it") },
                onOpenPhoto = { nav.navigate("viewer/$id/$it") },
            )
        }
        composable(Routes.CAPTURE, arguments = listOf(longArg("id"))) { entry ->
            val id = entry.arguments!!.getLong("id")
            val vm = viewModel(key = "capture$id") { CaptureVM(app, createSavedStateHandle(), id) }
            CaptureScreen(vm, onBack = { nav.popBackStack() }, onOpenPhoto = { folderId, photoId -> nav.navigate("viewer/$folderId/$photoId") })
        }
        composable(Routes.VIEWER, arguments = listOf(longArg("folderId"), longArg("photoId"))) { entry ->
            val folderId = entry.arguments!!.getLong("folderId")
            val photoId = entry.arguments!!.getLong("photoId")
            val vm = viewModel(key = "viewer$folderId") { ViewerVM(app, folderId) }
            ViewerScreen(vm, photoId, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val vm = viewModel { SettingsVM(app) }
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
