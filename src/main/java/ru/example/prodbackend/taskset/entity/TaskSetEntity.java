package ru.example.prodbackend.taskset.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.example.prodbackend.user.entity.UserEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_sets", indexes = @Index(name = "idx_task_sets_owner", columnList = "owner_id"))
public class TaskSetEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(length = 10000)
    private String description;
    @Builder.Default
    @OneToMany(mappedBy = "taskSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TaskSetItemEntity> items = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @PrePersist protected void onCreate() { createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); updatedAt = createdAt; }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
