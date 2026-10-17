package ru.example.prodbackend.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskStage;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;
    @Schema(example = "Упаковать кухню", description = "Название задачи")
    private String title;
    @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
    private String description;
    @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
    private TaskStage stage;

    @Schema(description = "Смещение срока в днях относительно даты переезда в UTC при явном применении шаблона")
    private int dueDateOffsetDays;

    @Schema(description = "true — личная готовая задача текущего пользователя, её можно изменить и удалить; false — общий образец")
    private boolean personal;
}
