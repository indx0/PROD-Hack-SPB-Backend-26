package ru.example.prodbackend.box.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;
import java.time.Instant;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "boxes", check = @CheckConstraint(
        name = "ck_boxes_positive_number", constraint = "number > 0"), uniqueConstraints = @UniqueConstraint(
        name = "uk_boxes_project_number", columnNames = {"project_id", "number"}), indexes = {
        @Index(name = "idx_boxes_project_status", columnList = "project_id,status"),
        @Index(name = "idx_boxes_room_project", columnList = "room_id,project_id")
    })
public class BoxEntity extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    // Explicit composite DDL keeps project and room writable without a mixed-writability join.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(
            name = "fk_boxes_room_project",
            foreignKeyDefinition = "foreign key (room_id, project_id) references rooms (id, project_id)"))
    private RoomEntity room;

    @Positive
    @Column(nullable = false)
    private int number;

    @NotNull
    @Setter(lombok.AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BoxStatus status = BoxStatus.PACKING;

    @Column(name = "contents_deferred", nullable = false)
    private boolean contentsDeferred;

    @Column(name = "contents_reminder_at")
    private Instant contentsReminderAt;

    /** Allows only a sequential forward step or an idempotent repeat. */
    public void changeStatus(BoxStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalArgumentException("Invalid box status transition: " + status + " -> " + next);
        }
        status = next;
    }

}
