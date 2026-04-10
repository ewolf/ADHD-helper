package com.roundtooit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.roundtooit.ui.dashboard.DashboardScreen
import com.roundtooit.ui.event.EventDetailScreen
import com.roundtooit.ui.login.LoginScreen
import com.roundtooit.ui.note.NoteScreen
import com.roundtooit.ui.task.TaskScreen

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val EVENT_DETAIL = "event/{eventId}"

    fun eventDetail(eventId: String) = "event/$eventId"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Routes.DASHBOARD else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToTasks = { navController.navigate(Routes.TASKS) },
                onNavigateToNotes = { navController.navigate(Routes.NOTES) },
                onNavigateToEvent = { eventId ->
                    navController.navigate(Routes.eventDetail(eventId))
                },
            )
        }

        composable(Routes.TASKS) {
            TaskScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.NOTES) {
            NoteScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EVENT_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            EventDetailScreen(
                eventId = eventId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
