package ru.example.prodbackend.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import ru.example.prodbackend.task.entity.TaskStage;
import java.util.UUID;

@Data
public class MoveTaskFilter {

    @Schema(description = "UUID папки этого переезда; фильтр объединяется через AND со stage/completed. Без параметра возвращаются все задачи проекта, включая задачи без папки")
    private UUID folderId;

    @Schema(description = "Этап переезда; отсутствие — все этапы")
    private TaskStage stage;

    @Schema(description = "true — выполненные, false — невыполненные; отсутствие — все")
    private Boolean completed;
}
