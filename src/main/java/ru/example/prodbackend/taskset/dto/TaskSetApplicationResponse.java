package ru.example.prodbackend.taskset.dto;

import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskSetApplicationResponse(UUID id, UUID projectId, UUID taskSetId, String taskSetName, Instant appliedAt, List<MoveTaskResponse> tasks) { }
