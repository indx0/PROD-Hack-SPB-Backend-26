package ru.example.prodbackend.move.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.example.prodbackend.user.entity.UserEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "project_participants", uniqueConstraints = @UniqueConstraint(name = "uk_project_participants_project_user", columnNames = {"project_id", "user_id"}),
        indexes = @Index(name = "idx_project_participants_user", columnList = "user_id"))
public class ProjectParticipantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    protected void onCreate() { addedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
