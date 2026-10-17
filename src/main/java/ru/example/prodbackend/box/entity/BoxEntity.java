package ru.example.prodbackend.box.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "boxes", uniqueConstraints = @UniqueConstraint(
        name = "uk_boxes_project_number", columnNames = {"project_id", "number"}
    ), check = @CheckConstraint(name = "ck_boxes_positive_number", constraint = "number > 0"), indexes = {
        @Index(name = "idx_boxes_project_status", columnList = "project_id,status"),
        @Index(name = "idx_boxes_room_project", columnList = "room_id,project_id")
    })
public class BoxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Positive
    @Column(name = "number", nullable = false)
    private int number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(
            name = "fk_boxes_room_project",
            foreignKeyDefinition = "foreign key (room_id, project_id) references rooms (id, project_id)")
    )
    private RoomEntity room;

    @Builder.Default
    @NotNull
    @Setter(lombok.AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoxStatus status = BoxStatus.PACKING;

    @Column(name = "contents_deferred", nullable = false)
    private boolean contentsDeferred;

    @Builder.Default
    @NotNull
    @Size(max = 255)
    @Column(name = "title", nullable = false, length = 255)
    private String title = "";

    @Column(name = "fragile", nullable = false)
    private boolean fragile;

    @Column(name = "first_day", nullable = false)
    private boolean firstDay;

    @Column(name = "carry_with_me", nullable = false)
    private boolean carryWithMe;

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

    /** Allows only a sequential forward step or an idempotent repeat. */
    public void changeStatus(BoxStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalArgumentException("Invalid box status transition: " + status + " -> " + next);
        }
        status = next;
    }

    /** Separate from normal forward transitions; business checks belong to BoxService. */
    public void correctStatus(BoxStatus target) {
        if (!status.canCorrectTo(target)) {
            throw new IllegalArgumentException("Invalid box status correction: " + status + " -> " + target);
        }
        status = target;
    }

}
