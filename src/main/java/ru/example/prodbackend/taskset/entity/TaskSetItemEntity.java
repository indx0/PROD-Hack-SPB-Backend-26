package ru.example.prodbackend.taskset.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskStage;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_set_items", uniqueConstraints = @UniqueConstraint(name = "uk_task_set_items_position", columnNames = {"task_set_id", "position"}))
public class TaskSetItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_set_id", nullable = false)
    private TaskSetEntity taskSet;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(length = 10000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private TaskStage stage;
    @Column(name = "offset_days", nullable = false)
    private Integer offsetDays;
    @Column(nullable = false)
    private int position;
}
