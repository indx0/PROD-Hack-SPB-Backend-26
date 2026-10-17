package ru.example.prodbackend.item.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.example.prodbackend.box.entity.BoxEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "items", indexes = {
        @Index(name = "idx_items_box_created_id", columnList = "box_id,created_at,id"),
        @Index(name = "idx_items_name", columnList = "name")
    })
public class ItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private BoxEntity box;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private boolean lost;

    @Builder.Default
    @Positive(message = "quantity not positive")
    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "first_day", nullable = false)
    private boolean firstDay;

    @Column(name = "packed_at")
    private Instant packedAt;

    @Column(name = "unpacked_at")
    private Instant unpackedAt;

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
