package net.alan.jupiter.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
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
}