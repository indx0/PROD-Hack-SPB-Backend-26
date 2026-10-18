package ru.example.prodbackend.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.task.entity.TaskStage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveTaskTemplateRequest {

    @Schema(example = "Упаковать кухню", description = "Название задачи")
    @NotBlank
    @Size(max = 255)
    private String title;

    @Schema(example = "Подготовить вещи к переезду", description = "Описание до 10000 символов; отсутствие или null очищает значение")
    @Size(max = 10000)
    private String description;

    @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
    @NotNull
    private TaskStage stage;

    @NotNull
    @Schema(description = "Срок относительно даты переезда: -2 — за два дня, 0 — в день переезда", example = "-2")
    private Integer dueDateOffsetDays;
}
