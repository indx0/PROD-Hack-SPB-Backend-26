package ru.example.prodbackend.taskfolder.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "task_folders")
public class TaskFolderEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private MoveProjectEntity project;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "color_key", nullable = false, length = 16)
    private String colorKey;

    @Column(name = "initial_name", nullable = false, length = 80, updatable = false)
    private String initialName;

    @Column(name = "initial_color_key", nullable = false, length = 16, updatable = false)
    private String initialColorKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
