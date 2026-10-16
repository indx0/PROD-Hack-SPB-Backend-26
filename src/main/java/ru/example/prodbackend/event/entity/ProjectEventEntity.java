package ru.example.prodbackend.event.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.user.entity.UserEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "project_events", indexes = {
        @Index(name = "idx_project_events_project_time", columnList = "project_id,occurred_at"),
        @Index(name = "idx_project_events_actor", columnList = "actor_id")
})
public class ProjectEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(lombok.AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserEntity actor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ProjectEventType eventType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 32)
    private EventObjectType objectType;

    @NotNull
    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    @Setter(lombok.AccessLevel.NONE)
    private Instant occurredAt;

    @NotBlank
    @Size(max = 2000)
    @Column(nullable = false, length = 2000)
    private String description;

    @PrePersist
    protected void onCreate() {
        occurredAt = Instant.now();
    }
}
