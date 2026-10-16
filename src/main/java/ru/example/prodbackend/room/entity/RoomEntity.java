package ru.example.prodbackend.room.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "rooms", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rooms_project_name", columnNames = {"project_id", "name"}),
        @UniqueConstraint(name = "uk_rooms_id_project", columnNames = {"id", "project_id"})
    }, indexes = @Index(name = "idx_rooms_project", columnList = "project_id"))
public class RoomEntity extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MoveProjectEntity project;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;


}
