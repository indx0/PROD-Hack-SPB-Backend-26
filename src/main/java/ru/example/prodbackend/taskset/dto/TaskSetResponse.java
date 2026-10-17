package ru.example.prodbackend.taskset.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskSetResponse(UUID id, String name, String description, List<TaskSetItemResponse> items, Instant createdAt, Instant updatedAt) { }
