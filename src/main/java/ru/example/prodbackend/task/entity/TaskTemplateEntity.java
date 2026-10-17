package ru.example.prodbackend.task.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.example.prodbackend.user.entity.UserEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_templates")
public class TaskTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** null — общий серверный образец; иначе личная готовая задача аккаунта. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    @Column(name = "due_date_offset_days", nullable = false)
    private int dueDateOffsetDays;

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
