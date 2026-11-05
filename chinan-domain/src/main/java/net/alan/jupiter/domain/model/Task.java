package net.alan.jupiter.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.alan.jupiter.domain.enums.TaskStatus;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "task")
@SQLRestriction("is_delete = false")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "task_no", unique = true)
    private String taskNo;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(name = "task_date")
    private LocalDate taskDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private DormBuilding building;

    @Column(name = "building_name")
    private String buildingName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    @Column(name = "floor_number")
    private String floorNumber;

    @Column(name = "room_numbers", length = 2000)
    private String roomNumbers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_counselor_id")
    private Counselor assignedCounselor;

    @Column(name = "assigned_counselor_name")
    private String assignedCounselorName;

    @Column(name = "total_rooms")
    private Integer totalRooms;

    @Column(name = "checked_rooms")
    @Builder.Default
    private Integer checkedRooms = 0;

    @Column(name = "deadline_time")
    private LocalDateTime deadlineTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    @Column(name = "is_delete")
    @Builder.Default
    private Boolean isDelete = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task that)) return false;
        return id != null && java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}