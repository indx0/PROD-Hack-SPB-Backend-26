package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.task.entity.TaskStage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSetItemRequest {

    @Schema(example = "Упаковать кухню", description = "Название задачи")
    @NotBlank
    @Size(max = 255)
    private String title;

    @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
    @Size(max = 10000)
    private String description;

    @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
    @NotNull
    private TaskStage stage;

    @Schema(example = "-2", description = "Смещение срока в календарных днях относительно даты переезда в UTC; отрицательное — до переезда")
    @NotNull
    private Integer offsetDays;
}
