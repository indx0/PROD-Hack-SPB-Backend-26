package ru.example.prodbackend.guide.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.example.prodbackend.common.entity.AuditedEntity;


@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "guides", uniqueConstraints = @UniqueConstraint(name = "uk_guides_code", columnNames = "code"))
public class GuideEntity extends AuditedEntity {
    @NotBlank
    @Size(max = 100)
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @NotBlank
    @Size(max = 20000)
    @Column(name = "content", nullable = false, length = 20000)
    private String content;

    @PositiveOrZero
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

}
