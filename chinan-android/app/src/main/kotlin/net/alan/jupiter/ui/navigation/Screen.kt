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

    /** A09 总汇总（本地聚合） */
    data object Summary : Screen("summary")

    /** A10 同步与上传（outbox / 同步 / 诊断） */
    data object Sync : Screen("sync")

    data object Settings : Screen("settings")

    /** 我的：从设置「账户」卡进入。 */
    data object Profile : Screen("me")
}
