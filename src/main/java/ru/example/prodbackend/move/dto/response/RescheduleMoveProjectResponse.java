package ru.example.prodbackend.move.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;

public record RescheduleMoveProjectResponse(
        @Schema(description = "Актуальный проект переезда")
        MoveProjectResponse project,
        @Schema(description = "Выбранные задачи с пересчитанными сроками")
        List<MoveTaskResponse> tasks) { }
