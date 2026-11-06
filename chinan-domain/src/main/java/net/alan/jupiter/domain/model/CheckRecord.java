package net.alan.jupiter.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.alan.jupiter.domain.enums.CheckStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "check_record", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"task_id", "room_id", "student_id"}),
        // 一次提交会为房间内每个学生各写一行，因此幂等键的唯一性必须带上 student_id
        @UniqueConstraint(columnNames = {"client_op_id", "student_id"})
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "room_number")
    private String roomNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "student_no")
    private String studentNo;

    @Column(name = "student_name")
    private String studentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_status")
    private CheckStatus checkStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "preset_status")
    private CheckStatus presetStatus;

    @Column(name = "preset_overridden")
    @Builder.Default
    private Boolean presetOverridden = false;

    @Column(name = "status_source")
    private String statusSource;

    private String remark;

    @Column(name = "proof_file")
    private String proofFile;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * 客户端幂等操作 ID（Android 离线补交时生成，UUID 字符串，长度上限 64）。
     * 同一 clientOpId 重复提交时服务端直接返回首次落库的记录，不再重复写入。
     * 可空：兼容未携带该字段的旧客户端与历史数据。
     */
    @Column(name = "client_op_id", length = 64)
    private String clientOpId;

    /**
     * 迟到标记：任务日期（taskDate）早于服务器接收日期（submittedAt 所在自然日）时为 true，
     * 即离线补交。由服务端在提交时计算，客户端不可指定；报表口径仍按 taskDate。
     */
    @Column(name = "late")
    @Builder.Default
    private Boolean late = false;

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
        if (!(o instanceof CheckRecord that)) return false;
        return id != null && java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}