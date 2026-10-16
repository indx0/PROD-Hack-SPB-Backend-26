package ru.example.prodbackend.move.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;
import java.time.LocalDate;
import ru.example.prodbackend.user.entity.UserEntity;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "move_projects", indexes = @Index(name = "idx_move_projects_owner", columnList = "owner_id"))
public class MoveProjectEntity extends AuditedEntity {
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


}
