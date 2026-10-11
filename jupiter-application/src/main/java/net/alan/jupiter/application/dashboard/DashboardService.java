package net.alan.jupiter.application.dashboard;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DormBuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final TaskRepository taskRepository;

    public DashboardStats getStats() {
        return DashboardStats.builder()
                .buildingCount(buildingRepository.countByIsActiveTrue())
                .roomCount(roomRepository.countByIsActiveTrue())
                .studentCount(studentRepository.countByIsActiveTrue())
                .activeTaskCount(taskRepository.countByStatus(TaskStatus.IN_PROGRESS))
                .build();
    }

    public List<Task> getRecentTasks() {
        return taskRepository.findTop5ByOrderByCreatedAtDesc();
    }

    @Data
    @Builder
    public static class DashboardStats {
        private long buildingCount;
        private long roomCount;
        private long studentCount;
        private long activeTaskCount;
    }
}