package ru.example.prodbackend.move.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.example.prodbackend.user.entity.UserEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "move_projects", indexes = @Index(name = "idx_move_projects_owner", columnList = "owner_id"))
public class MoveProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotNull
    @Column(name = "move_date", nullable = false)
    private LocalDate moveDate;

    @Size(max = 1000)
    @Column(name = "origin_address", nullable = true, length = 1000)
    private String originAddress;

    @Size(max = 1000)
    @Column(name = "destination_address", nullable = true, length = 1000)
    private String destinationAddress;

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
