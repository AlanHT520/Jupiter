package net.alan.jupiter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.alan.jupiter.ui.login.LoginScreen
import net.alan.jupiter.ui.login.LoginViewModel
import net.alan.jupiter.ui.overview.TaskOverviewScreen
import net.alan.jupiter.ui.profile.ProfileScreen
import net.alan.jupiter.ui.register.RegisterScreen
import net.alan.jupiter.ui.room.RoomCheckScreen
import net.alan.jupiter.ui.settings.SettingsScreen
import net.alan.jupiter.ui.summary.GlobalSummaryScreen
import net.alan.jupiter.ui.sync.SyncStatusScreen
import net.alan.jupiter.ui.tasks.TaskListScreen
import net.alan.jupiter.ui.tasks.TaskListViewModel

/**
 * 路由树：登录 → 今日任务 → 任务概览 → 房间查寝，其余为平级二级页
 * （汇总 / 同步 / 设置 / 我的）。刻意不引入底部导航：下钻路径需要随时可回退。
 */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
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
                },
                onRegisterClick = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onBackClick = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
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
                onSummaryClick = { navController.navigate(Screen.Summary.route) },
                onSyncClick = { navController.navigate(Screen.Sync.route) },
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
            val savedStateHandle = backStackEntry.savedStateHandle
            val shouldRefresh by savedStateHandle.getStateFlow("overviewShouldRefresh", false)
                .collectAsState()
            var refreshCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(shouldRefresh) {
                if (shouldRefresh) {
                    refreshCount++
                    savedStateHandle["overviewShouldRefresh"] = false
                }
            }
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
                onBackClick = { navController.popBackStack() },
                refreshTrigger = refreshCount
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
                // 概览页路由参数里带着楼栋名，房间页用它显示「N 人 · B4」
                buildingName = navController.previousBackStackEntry
                    ?.arguments
                    ?.getString("buildingName")
                    ?: "",
                onOpenSync = { navController.navigate(Screen.Sync.route) },
                onBackClick = { navController.popBackStack() },
                onSubmitSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("overviewShouldRefresh", true)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            val loginViewModel: LoginViewModel = hiltViewModel()
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onSyncClick = { navController.navigate(Screen.Sync.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
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
                onBackClick = { navController.popBackStack() },
                onSettingsClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Summary.route) {
            GlobalSummaryScreen(
                onBackClick = { navController.popBackStack() },
                onSyncClick = { navController.navigate(Screen.Sync.route) }
            )
        }

        composable(Screen.Sync.route) {
            SyncStatusScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
