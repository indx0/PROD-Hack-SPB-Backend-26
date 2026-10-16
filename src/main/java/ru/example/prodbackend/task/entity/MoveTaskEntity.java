package ru.example.prodbackend.task.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;
import java.time.Instant;
import java.time.LocalDate;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "move_tasks", indexes = {
        @Index(name = "idx_move_tasks_project", columnList = "project_id"),
        @Index(name = "idx_move_tasks_template", columnList = "template_id")
    })
public class MoveTaskEntity extends AuditedEntity {
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

    @Size(max = 255)
    @Column(name = "assignee_name", nullable = true, length = 255)
    private String assigneeName;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "template_id", nullable = true)
    private TaskTemplateEntity template;


}
