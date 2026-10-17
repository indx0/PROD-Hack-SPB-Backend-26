package ru.example.prodbackend.checklist.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.item.entity.ItemEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "first_day_checklist_items", indexes = @Index(name = "idx_first_day_items_project", columnList = "project_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_first_day_items_item", columnNames = "item_id"))
public class FirstDayChecklistItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", foreignKey = @ForeignKey(name = "fk_first_day_items_item"))
    private ItemEntity item;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
