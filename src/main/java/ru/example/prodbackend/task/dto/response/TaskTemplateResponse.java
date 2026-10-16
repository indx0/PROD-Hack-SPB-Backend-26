package ru.example.prodbackend.task.dto.response;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.example.prodbackend.task.entity.TaskStage;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateResponse {

    private UUID id;
    private String title;
    private String description;
    private TaskStage stage;

    @Schema(description = "Смещение срока в днях относительно даты переезда в UTC; используется при создании проекта")
    private int dueDateOffsetDays;
}
