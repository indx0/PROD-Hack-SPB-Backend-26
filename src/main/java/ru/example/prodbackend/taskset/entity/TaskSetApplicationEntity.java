package ru.example.prodbackend.taskset.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_set_applications", uniqueConstraints = @UniqueConstraint(name = "uk_task_set_applications_project_set", columnNames = {"project_id", "task_set_id"}),
        indexes = @Index(name = "idx_task_set_applications_set", columnList = "task_set_id"))
public class TaskSetApplicationEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_set_id", nullable = false)
    private TaskSetEntity taskSet;
    @Column(name = "task_set_name", nullable = false, length = 255)
    private String taskSetName;
    @Column(name = "folder_id", updatable = false)
    private UUID folderId;
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;
    @PrePersist protected void onCreate() { appliedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
