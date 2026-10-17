package ru.example.prodbackend.taskset.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskSetSummaryResponse(UUID id, String name, String description, Instant createdAt, Instant updatedAt) { }
