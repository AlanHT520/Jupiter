package net.alan.jupiter.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object TaskList : Screen("tasks")
    data class TaskOverview(val taskId: String, val taskNo: String, val buildingName: String) :
        Screen("tasks/{taskId}/overview/{taskNo}/{buildingName}") {
        companion object {
            val pattern = "tasks/{taskId}/overview/{taskNo}/{buildingName}"
        }
    }
    data class RoomCheck(val taskId: String, val roomId: String, val roomNumber: String) :
        Screen("tasks/{taskId}/rooms/{roomId}/{roomNumber}") {
        companion object {
            val pattern = "tasks/{taskId}/rooms/{roomId}/{roomNumber}"
        }
    }
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")

    /** A09 总汇总（本地聚合） */
    data object Summary : Screen("summary")

    /** A10 缓存与同步（outbox / 补交 / 清除数据） */
    data object Sync : Screen("sync")
}