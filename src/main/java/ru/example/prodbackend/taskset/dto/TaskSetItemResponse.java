package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.task.entity.TaskStage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSetItemResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;

    @Schema(example = "Упаковать кухню", description = "Название задачи")
    private String title;

    @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
    private String description;

    @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
    private TaskStage stage;

    @Schema(example = "-2", description = "Смещение срока в календарных днях относительно даты переезда в UTC; отрицательное — до переезда")
    private Integer offsetDays;

    @Schema(example = "0", description = "Позиция задачи в наборе, начиная с 0")
    private int position;
}
