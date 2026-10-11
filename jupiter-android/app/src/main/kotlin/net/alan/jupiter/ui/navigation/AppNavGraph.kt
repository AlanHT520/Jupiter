package net.alan.jupiter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import net.alan.jupiter.ui.login.LoginScreen
import net.alan.jupiter.ui.login.LoginViewModel
import net.alan.jupiter.ui.overview.TaskOverviewScreen
import net.alan.jupiter.ui.room.RoomCheckScreen
import net.alan.jupiter.ui.profile.ProfileScreen
import net.alan.jupiter.ui.settings.SettingsScreen
import net.alan.jupiter.ui.tasks.TaskListScreen
import net.alan.jupiter.ui.tasks.TaskListViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController = androidx.navigation.compose.rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.TaskList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.TaskList.route) {
            val taskListViewModel: TaskListViewModel = hiltViewModel()
            TaskListScreen(
                onTaskClick = { taskId, taskNo, buildingName ->
                    navController.navigate(
                        Screen.TaskOverview.pattern
                            .replace("{taskId}", taskId)
                            .replace("{taskNo}", taskNo)
                            .replace("{buildingName}", buildingName)
                    )
                },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onLogout = {
                    taskListViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.TaskOverview.pattern,
            arguments = listOf(
                navArgument("taskId") { type = NavType.StringType },
                navArgument("taskNo") { type = NavType.StringType },
                navArgument("buildingName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
            val taskNo = backStackEntry.arguments?.getString("taskNo") ?: ""
            val buildingName = backStackEntry.arguments?.getString("buildingName") ?: ""
            TaskOverviewScreen(
                taskId = taskId,
                taskNo = taskNo,
                buildingName = buildingName,
                onRoomClick = { roomId, roomNumber ->
                    navController.navigate(
                        Screen.RoomCheck.pattern
                            .replace("{taskId}", taskId)
                            .replace("{roomId}", roomId)
                            .replace("{roomNumber}", roomNumber)
                    )
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RoomCheck.pattern,
            arguments = listOf(
                navArgument("taskId") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
                navArgument("roomNumber") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val roomNumber = backStackEntry.arguments?.getString("roomNumber") ?: ""
            RoomCheckScreen(
                taskId = taskId,
                roomId = roomId,
                roomNumber = roomNumber,
                onBackClick = { navController.popBackStack() },
                onSubmitSuccess = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val loginViewModel: LoginViewModel = hiltViewModel()
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onLogout = {
                    loginViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}