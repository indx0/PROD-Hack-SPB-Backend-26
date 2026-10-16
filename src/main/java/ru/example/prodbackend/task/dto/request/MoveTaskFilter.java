package ru.example.prodbackend.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import ru.example.prodbackend.task.entity.TaskStage;

@Data
public class MoveTaskFilter {

    @Schema(description = "Этап переезда; отсутствие — все этапы")
    private TaskStage stage;

    @Schema(description = "true — выполненные, false — невыполненные; отсутствие — все")
    private Boolean completed;
}
