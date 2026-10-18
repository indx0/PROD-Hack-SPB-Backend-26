package ru.example.prodbackend.move.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleMoveProjectResponse {

    @Schema(description = "Актуальный проект переезда")
    private MoveProjectResponse project;

    @Schema(description = "Выбранные задачи с пересчитанными сроками")
    private List<MoveTaskResponse> tasks;
}
