package com.visualizerstudio.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.visualizerstudio.app.ui.home.HomeScreen
import com.visualizerstudio.app.ui.preset.PresetScreen
import com.visualizerstudio.app.ui.queue.QueueScreen
import com.visualizerstudio.app.ui.wizard.WizardScreen

/**
 * File navigasi ringan yang menghubungkan 4 layar Fase 4. Bukan bagian dari path yang
 * dipatok fase manapun di Appendix A.2 (folder `ui/` sendiri netral) — Fase 5 bisa menambah
 * route baru (`canvas_editor/{taskId}`, `shader_studio`) dengan menambah `composable(...)`
 * baru di file ini SAJA kalau memang perlu (tidak menghapus/mengubah route yang sudah ada
 * di sini), atau lebih aman: buat NavGraph terpisah dan nav ke sana, supaya file ini tidak
 * perlu disentuh sama sekali oleh Fase 5.
 */
private object Routes {
    const val HOME = "home"
    const val WIZARD = "wizard"
    const val QUEUE = "queue"
    const val PRESET = "preset"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onCreateNewTask = { navController.navigate(Routes.WIZARD) },
                onOpenQueue = { navController.navigate(Routes.QUEUE) },
                onOpenPresets = { navController.navigate(Routes.PRESET) }
            )
        }
        composable(Routes.WIZARD) {
            WizardScreen(
                onTaskSubmitted = { navController.navigate(Routes.QUEUE) { popUpTo(Routes.HOME) } },
                onClose = { navController.popBackStack() }
            )
        }
        composable(Routes.QUEUE) {
            QueueScreen(onAddNewTask = { navController.navigate(Routes.WIZARD) })
        }
        composable(Routes.PRESET) {
            PresetScreen(onApplyToNewTask = { navController.navigate(Routes.WIZARD) })
        }
    }
}
