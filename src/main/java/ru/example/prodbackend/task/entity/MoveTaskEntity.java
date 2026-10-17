package ru.example.prodbackend.task.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "move_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_move_tasks_project_template",
                columnNames = {"project_id", "template_id"}),
        indexes = {
                @Index(name = "idx_move_tasks_project", columnList = "project_id"),
                @Index(name = "idx_move_tasks_template", columnList = "template_id")
        })
public class MoveTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Size(max = 10000)
    @Column(name = "description", nullable = true, length = 10000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStage stage;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Builder.Default
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "due_type", nullable = false, length = 16)
    private TaskDueType dueType = TaskDueType.FIXED;

    @Column(name = "offset_days")
    private Integer offsetDays;

    @Size(max = 255)
    @Column(name = "assignee_name", nullable = true, length = 255)
    private String assigneeName;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "template_id", nullable = true)
    private TaskTemplateEntity template;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
