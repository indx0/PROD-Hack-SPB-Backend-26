package ru.example.prodbackend.task.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;


@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "task_templates", uniqueConstraints = @UniqueConstraint(name = "uk_task_templates_code", columnNames = "code"))
public class TaskTemplateEntity extends AuditedEntity {
    @NotBlank
    @Size(max = 100)
    @Column(name = "code", nullable = false, length = 100)
    private String code;

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

    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @PositiveOrZero
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

}
