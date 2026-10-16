package ru.example.prodbackend.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;
import ru.example.prodbackend.box.entity.BoxEntity;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "items", indexes = {
        @Index(name = "idx_items_box", columnList = "box_id"),
        @Index(name = "idx_items_name", columnList = "name")
    })
public class ItemEntity extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private BoxEntity box;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private boolean lost;

}
