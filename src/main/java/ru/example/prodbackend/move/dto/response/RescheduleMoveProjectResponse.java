package ru.example.prodbackend.move.dto.response;

import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import java.util.List;

public record RescheduleMoveProjectResponse(MoveProjectResponse project, List<MoveTaskResponse> tasks) { }
