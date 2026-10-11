package net.alan.jupiter.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "student")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "student_no", nullable = false)
    private String studentNo;

    @Column(nullable = false)
    private String name;

    private String gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private ClassInfo classInfo;

    @Column(name = "class_name")
    private String className;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "room_number")
    private String roomNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private DormBuilding building;

    @Column(name = "building_name")
    private String buildingName;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @Column(name = "bed_number")
    private String bedNumber;

    @Column(name = "phone")
    private String phone;

    @Column(name = "id_card")
    private String idCard;

    @Column(name = "enrollment_year")
    private Integer enrollmentYear;

    @Column(name = "is_new_student")
    @Builder.Default
    private Boolean isNewStudent = false;

    @Column(name = "long_sick_leave_start_date")
    private LocalDate longSickLeaveStartDate;

    @Column(name = "long_sick_leave_end_date")
    private LocalDate longSickLeaveEndDate;

    @Column(name = "military_start_date")
    private LocalDate militaryStartDate;

    @Column(name = "military_end_date")
    private LocalDate militaryEndDate;

    @Column(name = "intern_start_date")
    private LocalDate internStartDate;

    @Column(name = "intern_end_date")
    private LocalDate internEndDate;

    @Column(name = "day_student_start_date")
    private LocalDate dayStudentStartDate;

    @Column(name = "day_student_end_date")
    private LocalDate dayStudentEndDate;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    private String remark;

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
        if (!(o instanceof Student that)) return false;
        return id != null && java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}