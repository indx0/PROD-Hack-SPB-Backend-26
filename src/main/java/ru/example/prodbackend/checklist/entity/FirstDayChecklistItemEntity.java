package ru.example.prodbackend.checklist.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;
import java.time.Instant;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "first_day_checklist_items", indexes = @Index(name = "idx_first_day_items_project", columnList = "project_id"))
public class FirstDayChecklistItemEntity extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @PositiveOrZero
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "completed_at")
    private Instant completedAt;

}
