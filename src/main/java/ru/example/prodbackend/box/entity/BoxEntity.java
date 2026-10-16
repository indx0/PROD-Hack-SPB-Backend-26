package ru.example.prodbackend.box.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "boxes", check = @CheckConstraint(
        name = "ck_boxes_positive_number", constraint = "number > 0"), uniqueConstraints = @UniqueConstraint(
        name = "uk_boxes_project_number", columnNames = {"project_id", "number"}), indexes = {
        @Index(name = "idx_boxes_project_status", columnList = "project_id,status"),
        @Index(name = "idx_boxes_room_project", columnList = "room_id,project_id")
    })
public class BoxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    // Explicit composite DDL keeps project and room writable without a mixed-writability join.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(
            name = "fk_boxes_room_project",
            foreignKeyDefinition = "foreign key (room_id, project_id) references rooms (id, project_id)"))
    private RoomEntity room;

    @Builder.Default
    @NotNull
    @Setter(lombok.AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BoxStatus status = BoxStatus.PACKING;

    @Column(name = "contents_deferred", nullable = false)
    private boolean contentsDeferred;

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

    /** Allows only a sequential forward step or an idempotent repeat. */
    public void changeStatus(BoxStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalArgumentException("Invalid box status transition: " + status + " -> " + next);
        }
        status = next;
    }

}
